#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CampusFormat 端到端冒烟脚本（仅使用 Python 标准库）。

覆盖链路：
  注册/登录 -> 上传 SRT -> 创建 srt→vtt 任务 -> 轮询状态
  -> 使用一次性下载令牌匿名下载 -> 验证令牌不可重放
  -> 提交反馈 -> 查询我的反馈
  ->（可选）验证登录限流返回 1011

用法：
  python smoke-test.py
  python smoke-test.py --base-url http://localhost:8080/api
  python smoke-test.py --check-rate-limit

退出码：0 = 全部通过；1 = 存在失败步骤。
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

DEFAULT_BASE = os.environ.get("CAMPUS_SMOKE_BASE", "http://localhost:8080/api")
SRT_CONTENT = (
    "1\n"
    "00:00:01,000 --> 00:00:03,000\n"
    "Hello CampusFormat\n"
    "\n"
    "2\n"
    "00:00:04,000 --> 00:00:06,000\n"
    "End-to-end smoke test\n"
).encode("utf-8")

PASSED = []
FAILED = []


def ok(step, detail=""):
    PASSED.append(step)
    print("[OK]   %s%s" % (step, (" - " + detail) if detail else ""))


def fail(step, detail=""):
    FAILED.append(step)
    print("[FAIL] %s%s" % (step, (" - " + detail) if detail else ""))


def call(base, method, path, payload=None, token=None, body=None, content_type=None, timeout=30):
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
    except Exception as error:  # 网络错误直接上抛，由调用方记录
        raise RuntimeError("请求失败: %s %s -> %s" % (method, path, error))

    if "application/json" in ctype:
        try:
            return status, json.loads(raw.decode("utf-8"))
        except Exception:
            return status, {"_raw": raw.decode("utf-8", "replace")}
    return status, raw


def multipart_srt():
    boundary = "----CampusFormatSmoke" + uuid.uuid4().hex
    parts = [
        ("--" + boundary).encode("utf-8"),
        b'Content-Disposition: form-data; name="file"; filename="smoke.srt"',
        b"Content-Type: application/x-subrip",
        b"",
        SRT_CONTENT,
        ("--" + boundary + "--").encode("utf-8"),
        b"",
    ]
    return b"\r\n".join(parts), "multipart/form-data; boundary=" + boundary


def ensure_account(base, username, password):
    status, body = call(base, "POST", "/auth/register",
                        {"username": username, "password": password, "role": "STUDENT"})
    if isinstance(body, dict) and body.get("code") == 200:
        ok("注册", "username=%s" % username)
    else:
        detail = body.get("message") if isinstance(body, dict) else str(body)
        print("[INFO] 注册未成功（可能已存在），尝试直接登录: %s" % detail)

    status, body = call(base, "POST", "/auth/login", {"username": username, "password": password})
    if not isinstance(body, dict) or body.get("code") != 200:
        raise RuntimeError("登录失败: %s" % json.dumps(body, ensure_ascii=False))
    token = body["data"]["token"]
    ok("登录", "role=%s" % body["data"]["user"].get("role"))
    return token


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=DEFAULT_BASE)
    parser.add_argument("--username", default="smoke_" + time.strftime("%m%d%H%M%S"))
    parser.add_argument("--password", default="Smoke@123456")
    parser.add_argument("--timeout", type=int, default=180, help="任务轮询最长秒数")
    parser.add_argument("--check-rate-limit", action="store_true", help="额外验证登录限流（1011）")
    args = parser.parse_args()

    base = args.base_url
    print("冒烟目标: %s" % base)
    print("测试账号: %s" % args.username)
    print("-" * 60)

    # 1. 注册 / 登录
    token = ensure_account(base, args.username, args.password)

    # 2. 上传 SRT
    body, ctype = multipart_srt()
    status, uploaded = call(base, "POST", "/files", token=token, body=body, content_type=ctype)
    if not isinstance(uploaded, dict) or uploaded.get("code") != 200:
        raise RuntimeError("上传失败: %s" % json.dumps(uploaded, ensure_ascii=False))
    file_id = uploaded["data"]["fileId"]
    ext = uploaded["data"].get("ext")
    ok("上传文件", "fileId=%s, ext=%s, recommended=%s" %
       (file_id, ext, uploaded["data"].get("recommendedFormats")))

    # 3. 创建任务 srt -> vtt
    status, created = call(base, "POST", "/tasks",
                           {"sourceFileId": file_id, "targetFormat": "vtt"}, token=token)
    if not isinstance(created, dict) or created.get("code") != 200:
        raise RuntimeError("创建任务失败: %s" % json.dumps(created, ensure_ascii=False))
    task_id = created["data"]["id"]
    ok("创建任务", "taskId=%s, status=%s" % (task_id, created["data"].get("status")))

    # 4. 轮询任务状态
    deadline = time.time() + args.timeout
    task = None
    last_status = None
    while time.time() < deadline:
        status, detail = call(base, "GET", "/tasks/%s" % task_id, token=token)
        if not isinstance(detail, dict) or detail.get("code") != 200:
            raise RuntimeError("查询任务失败: %s" % json.dumps(detail, ensure_ascii=False))
        task = detail["data"]
        last_status = task.get("status")
        if last_status in ("SUCCESS", "FAILED", "CANCELLED", "EXPIRED"):
            break
        time.sleep(2)

    if last_status != "SUCCESS":
        raise RuntimeError("任务未成功: status=%s, error=%s" %
                           (last_status, task.get("errorMsg") if task else "unknown"))
    ok("任务转换", "status=SUCCESS, finishedAt=%s" % task.get("finishedAt"))

    download_token = task.get("downloadToken")
    if not download_token:
        raise RuntimeError("任务详情未返回 downloadToken")
    ok("获取下载令牌", "token 长度=%s" % len(download_token))

    # 5. 不携带登录态，仅用令牌下载
    status, content = call(base, "GET", "/tasks/%s/download?token=%s" % (task_id, download_token))
    if isinstance(content, bytes) and content.lstrip().upper().startswith(b"WEBVTT"):
        ok("令牌匿名下载", "首行 WEBVTT，%s bytes" % len(content))
    else:
        fail("令牌匿名下载", "返回非 VTT 内容: %r" % (content[:80] if isinstance(content, bytes) else content))

    # 6. 同一令牌再次下载，应被拒绝（一次性）
    status, replay = call(base, "GET", "/tasks/%s/download?token=%s" % (task_id, download_token))
    if isinstance(replay, dict) and replay.get("code") == 1006:
        ok("令牌不可重放", "code=1006")
    else:
        fail("令牌不可重放", "期望 code=1006，实际=%s" % json.dumps(replay, ensure_ascii=False)
             if isinstance(replay, dict) else "非 JSON 响应")

    # 7. 提交反馈
    status, feedback = call(base, "POST", "/feedback", {
        "taskId": task_id,
        "rating": 5,
        "category": "EXPERIENCE",
        "title": "端到端冒烟",
        "comment": "自动冒烟脚本提交：转换与下载链路正常。",
        "anonymous": False,
    }, token=token)
    if isinstance(feedback, dict) and feedback.get("code") == 200:
        ok("提交反馈", "feedbackId=%s, status=%s" %
           (feedback["data"].get("id"), feedback["data"].get("status")))
    else:
        fail("提交反馈", json.dumps(feedback, ensure_ascii=False))

    # 8. 查询我的反馈
    status, mine = call(base, "GET", "/feedback/mine?pageNum=1&pageSize=10", token=token)
    if isinstance(mine, dict) and mine.get("code") == 200 and mine["data"].get("total", 0) >= 1:
        ok("查询我的反馈", "total=%s" % mine["data"]["total"])
    else:
        fail("查询我的反馈", json.dumps(mine, ensure_ascii=False))

    # 9. 可选：登录限流
    if args.check_rate_limit:
        limited = False
        for index in range(15):
            status, body = call(base, "POST", "/auth/login",
                                {"username": args.username, "password": args.password})
            if isinstance(body, dict) and body.get("code") == 1011:
                ok("登录限流", "第 %s 次请求返回 1011" % (index + 1))
                limited = True
                break
        if not limited:
            fail("登录限流", "15 次请求内未触发 1011")

    print("-" * 60)
    print("通过: %s 项，失败: %s 项" % (len(PASSED), len(FAILED)))
    if FAILED:
        print("失败步骤: %s" % ", ".join(FAILED))
        return 1
    print("冒烟结果: PASS")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print("[ERROR] %s" % exc)
        sys.exit(1)
