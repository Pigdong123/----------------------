#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
稳定性与负向测试：
  1. 并发创建 8 个 SRT→VTT 任务，验证队列/CAS/配额在并发下不重复、不丢失
  2. 非法目标格式应被拒绝（1003/400）
  3. 普通用户访问运维接口应 403
  4. 无令牌/无登录下载应被拒绝（401/1006）
"""

import argparse
import base64
import concurrent.futures
import json
import sys
import time
import urllib.error
import urllib.request
import uuid

DEFAULT_BASE = "http://localhost:8080/api"
SRT = (
    "1\n00:00:01,000 --> 00:00:03,000\nConcurrency test\n\n"
    "2\n00:00:04,000 --> 00:00:06,000\nSecond cue\n"
).encode("utf-8")

PASSED = []
FAILED = []


def ok(step, detail=""):
    PASSED.append(step)
    print("[OK]   %s%s" % (step, (" - " + detail) if detail else ""))


def fail(step, detail=""):
    FAILED.append(step)
    print("[FAIL] %s%s" % (step, (" - " + detail) if detail else ""))


def call(base, method, path, payload=None, token=None, body=None, content_type=None, timeout=120):
    url = base.rstrip("/") + path
    headers = {}
    data = None
    if body is not None:
        data = body
        if content_type:
            headers["Content-Type"] = content_type
    elif payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read()
            status = response.status
            ctype = response.headers.get("Content-Type", "")
    except urllib.error.HTTPError as error:
        raw = error.read()
        status = error.code
        ctype = error.headers.get("Content-Type", "")
    if "application/json" in ctype:
        try:
            return status, json.loads(raw.decode("utf-8"))
        except Exception:
            return status, {"_raw": raw.decode("utf-8", "replace")}
    return status, raw


def login(base, username, password):
    status, body = call(base, "POST", "/auth/login", {"username": username, "password": password})
    if isinstance(body, dict) and body.get("code") == 200:
        return body["data"]["token"]
    return None


def ensure_user(base, username, password):
    call(base, "POST", "/auth/register",
         {"username": username, "password": password, "role": "STUDENT"})
    token = login(base, username, password)
    if not token:
        raise RuntimeError("无法登录测试用户: " + username)
    return token


def token_file_id(task):
    """优先用 resultFileId；DTO 未暴露时解析下载令牌 payload 中的 fileId。"""
    if task.get("resultFileId") is not None:
        return task["resultFileId"]
    token = task.get("downloadToken")
    if not token or token.count(".") != 2:
        return None
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    try:
        data = json.loads(base64.urlsafe_b64decode(payload).decode("utf-8"))
        return data.get("fileId")
    except Exception:
        return None


def upload_srt(base, token):
    boundary = "----CampusFormatStability" + uuid.uuid4().hex
    parts = [
        ("--" + boundary).encode(),
        b'Content-Disposition: form-data; name="file"; filename="stability.srt"',
        b"Content-Type: application/x-subrip",
        b"",
        SRT,
        ("--" + boundary + "--").encode(),
        b"",
    ]
    body = b"\r\n".join(parts)
    status, result = call(base, "POST", "/files", token=token, body=body,
                          content_type="multipart/form-data; boundary=" + boundary)
    if not isinstance(result, dict) or result.get("code") != 200:
        raise RuntimeError("上传失败: " + json.dumps(result, ensure_ascii=False))
    return result["data"]["fileId"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=DEFAULT_BASE)
    parser.add_argument("--count", type=int, default=8)
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args()

    base = args.base_url
    username = "stability_" + time.strftime("%m%d%H%M%S")
    print("稳定性测试: %s（并发 %s）" % (base, args.count))
    print("-" * 60)

    token = ensure_user(base, username, "Smoke@123456")
    ok("准备测试用户", username)

    file_id = upload_srt(base, token)
    ok("上传源文件", "fileId=%s" % file_id)

    started = time.time()

    def create_one(index):
        status, body = call(base, "POST", "/tasks",
                            {"sourceFileId": file_id, "targetFormat": "vtt"}, token=token)
        if not isinstance(body, dict) or body.get("code") != 200:
            raise RuntimeError("任务 %s 创建失败: %s" % (index, json.dumps(body, ensure_ascii=False)))
        return body["data"]["id"]

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.count) as pool:
        task_ids = list(pool.map(create_one, range(args.count)))
    ok("并发创建任务", "%s 个任务全部入队" % len(task_ids))

    pending = set(task_ids)
    results = {}
    deadline = time.time() + args.timeout
    while pending and time.time() < deadline:
        for task_id in list(pending):
            status, body = call(base, "GET", "/tasks/%s" % task_id, token=token)
            if not isinstance(body, dict) or body.get("code") != 200:
                raise RuntimeError("查询任务 %s 失败" % task_id)
            task = body["data"]
            if task.get("status") == "SUCCESS":
                results[task_id] = task
                pending.remove(task_id)
            elif task.get("status") in ("FAILED", "CANCELLED", "EXPIRED"):
                raise RuntimeError("任务 %s 失败: %s" % (task_id, task.get("errorMsg")))
        if pending:
            time.sleep(1)

    if pending:
        fail("并发任务完成", "超时未完成: %s" % sorted(pending))
    else:
        elapsed = time.time() - started
        result_file_ids = {token_file_id(task) for task in results.values()}
        if None not in result_file_ids and len(result_file_ids) == len(task_ids):
            ok("并发任务完成", "%s/%s 成功，产物唯一，耗时 %.1fs" %
               (len(results), len(task_ids), elapsed))
        else:
            fail("并发任务完成", "产物 fileId 异常: %s" % result_file_ids)

    # 负向 1：非法目标格式
    status, body = call(base, "POST", "/tasks",
                        {"sourceFileId": file_id, "targetFormat": "exe"}, token=token)
    code = body.get("code") if isinstance(body, dict) else None
    if code in (400, 1003):
        ok("非法目标格式被拒绝", "code=%s" % code)
    else:
        fail("非法目标格式被拒绝", "实际 code=%s" % code)

    # 负向 2：普通用户访问运维接口
    status, body = call(base, "GET", "/ops/engines", token=token)
    if status == 403 or (isinstance(body, dict) and body.get("code") == 403):
        ok("越权访问运维接口被拒绝", "HTTP %s" % status)
    else:
        fail("越权访问运维接口被拒绝", "HTTP %s, body=%s" % (status, body))

    # 负向 3：无令牌、无登录下载
    first_task_id = task_ids[0]
    status, body = call(base, "GET", "/tasks/%s/download" % first_task_id)
    code = body.get("code") if isinstance(body, dict) else None
    if code in (401, 1006):
        ok("未登录下载被拒绝", "code=%s" % code)
    else:
        fail("未登录下载被拒绝", "实际 code=%s" % code)

    print("-" * 60)
    print("通过: %s 项，失败: %s 项" % (len(PASSED), len(FAILED)))
    if FAILED:
        print("失败步骤: %s" % ", ".join(FAILED))
        return 1
    print("稳定性测试: PASS")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print("[ERROR] %s" % exc)
        sys.exit(1)
