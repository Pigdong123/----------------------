#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
扩展冒烟：能力目录 + 图片/文档/字幕三类引擎 + 运维健康检查。

用法：
  python extended-smoke.py
  python extended-smoke.py --admin-user smoke_xxx --admin-pass Smoke@123456
"""

import argparse
import io
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
import zipfile

from PIL import Image

DEFAULT_BASE = "http://localhost:8080/api"
SRT = (
    "1\n"
    "00:00:01,000 --> 00:00:03,000\n"
    "Hello CampusFormat\n"
    "\n"
    "2\n"
    "00:00:04,000 --> 00:00:06,000\n"
    "Extended smoke test\n"
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


def multipart(filename, content, content_type):
    boundary = "----CampusFormatExt" + uuid.uuid4().hex
    parts = [
        ("--" + boundary).encode(),
        ('Content-Disposition: form-data; name="file"; filename="%s"' % filename).encode(),
        ("Content-Type: " + content_type).encode(),
        b"",
        content,
        ("--" + boundary + "--").encode(),
        b"",
    ]
    return b"\r\n".join(parts), "multipart/form-data; boundary=" + boundary


def upload(base, token, filename, content, content_type):
    body, ctype = multipart(filename, content, content_type)
    status, result = call(base, "POST", "/files", token=token, body=body, content_type=ctype)
    if not isinstance(result, dict) or result.get("code") != 200:
        raise RuntimeError("上传失败: " + json.dumps(result, ensure_ascii=False))
    return result["data"]["fileId"]


def create_and_wait(base, token, file_id, target, timeout_seconds=180):
    status, created = call(base, "POST", "/tasks",
                           {"sourceFileId": file_id, "targetFormat": target}, token=token)
    if not isinstance(created, dict) or created.get("code") != 200:
        raise RuntimeError("创建任务失败: " + json.dumps(created, ensure_ascii=False))
    task_id = created["data"]["id"]

    deadline = time.time() + timeout_seconds
    last = None
    while time.time() < deadline:
        status, detail = call(base, "GET", "/tasks/%s" % task_id, token=token)
        if not isinstance(detail, dict) or detail.get("code") != 200:
            raise RuntimeError("查询任务失败: " + json.dumps(detail, ensure_ascii=False))
        task = detail["data"]
        last = task.get("status")
        if last == "SUCCESS":
            return task
        if last in ("FAILED", "CANCELLED", "EXPIRED"):
            raise RuntimeError("任务未成功: status=%s, error=%s" % (last, task.get("errorMsg")))
        time.sleep(2)
    raise RuntimeError("任务超时: status=%s" % last)


def download_by_token(base, task):
    token = task.get("downloadToken")
    if not token:
        raise RuntimeError("任务详情未返回 downloadToken")
    status, content = call(base, "GET", "/tasks/%s/download?token=%s" % (task["id"], token))
    if not isinstance(content, bytes):
        raise RuntimeError("下载返回非二进制: " + json.dumps(content, ensure_ascii=False))
    return content


def make_docx():
    content_types = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/word/document.xml" '
        'ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
        '</Types>'
    )
    rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" '
        'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" '
        'Target="word/document.xml"/>'
        '</Relationships>'
    )
    document = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        '<w:body><w:p><w:r><w:t>Hello CampusFormat</w:t></w:r></w:p></w:body>'
        '</w:document>'
    )
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", content_types)
        archive.writestr("_rels/.rels", rels)
        archive.writestr("word/document.xml", document)
    return buffer.getvalue()


def make_png():
    buffer = io.BytesIO()
    Image.new("RGB", (64, 64), (31, 158, 137)).save(buffer, format="PNG")
    return buffer.getvalue()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=DEFAULT_BASE)
    parser.add_argument("--user", default="smoke_1117084917")
    parser.add_argument("--password", default="Smoke@123456")
    parser.add_argument("--admin-user", default="smoke_1117084824")
    parser.add_argument("--admin-pass", default="Smoke@123456")
    args = parser.parse_args()

    base = args.base_url
    print("扩展冒烟: %s" % base)
    print("-" * 60)

    token = login(base, args.user, args.password)
    if not token:
        args.user = "verify_" + time.strftime("%m%d%H%M%S")
        token = ensure_user(base, args.user, args.password)
        ok("准备测试用户", args.user)
    else:
        ok("登录测试用户", args.user)

    # 1. 能力目录
    status, catalog = call(base, "GET", "/formats", token=token)
    if isinstance(catalog, dict) and catalog.get("code") == 200:
        formats = catalog["data"].get("formats", [])
        available = sum(1 for item in formats if item.get("available"))
        grouped = catalog["data"].get("grouped", {})
        ok("能力目录", "total=%s, available=%s, groups=%s" %
           (catalog["data"].get("total"), available, ",".join(sorted(grouped.keys()))))
    else:
        fail("能力目录", json.dumps(catalog, ensure_ascii=False))

    # 2. PNG -> WebP
    try:
        file_id = upload(base, token, "pixel.png", make_png(), "image/png")
        task = create_and_wait(base, token, file_id, "webp")
        content = download_by_token(base, task)
        if content[:4] == b"RIFF" and content[8:12] == b"WEBP":
            ok("图片引擎 PNG→WebP", "taskId=%s, %s bytes" % (task["id"], len(content)))
        else:
            fail("图片引擎 PNG→WebP", "文件头不符合 WebP: %r" % content[:16])
    except Exception as exc:
        fail("图片引擎 PNG→WebP", str(exc))

    # 3. DOCX -> PDF（LibreOffice）
    try:
        file_id = upload(base, token, "sample.docx",
                         make_docx(),
                         "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        task = create_and_wait(base, token, file_id, "pdf")
        content = download_by_token(base, task)
        if content[:4] == b"%PDF":
            ok("文档引擎 DOCX→PDF", "taskId=%s, %s bytes" % (task["id"], len(content)))
        else:
            fail("文档引擎 DOCX→PDF", "文件头不是 PDF: %r" % content[:16])
    except Exception as exc:
        fail("文档引擎 DOCX→PDF", str(exc))

    # 4. SRT -> ASS
    try:
        file_id = upload(base, token, "sample.srt", SRT, "application/x-subrip")
        task = create_and_wait(base, token, file_id, "ass")
        content = download_by_token(base, task)
        text = content.decode("utf-8", "replace")
        if text.startswith("[Script Info]"):
            ok("字幕引擎 SRT→ASS", "taskId=%s, %s bytes" % (task["id"], len(content)))
        else:
            fail("字幕引擎 SRT→ASS", "输出头不符合 ASS: %r" % text[:40])
    except Exception as exc:
        fail("字幕引擎 SRT→ASS", str(exc))

    # 5. 运维端（需要管理员）
    admin_token = login(base, args.admin_user, args.admin_pass)
    if admin_token:
        status, engines = call(base, "GET", "/ops/engines", token=admin_token)
        if isinstance(engines, dict) and engines.get("code") == 200:
            ok("引擎健康检查", "healthy=%s, engines=%s" %
               (engines["data"].get("healthy"), "; ".join(engines["data"].get("engines", []))))
        else:
            fail("引擎健康检查", json.dumps(engines, ensure_ascii=False))

        status, queue = call(base, "GET", "/ops/queue", token=admin_token)
        if isinstance(queue, dict) and queue.get("code") == 200:
            ok("队列监控", "queued=%s" % queue["data"].get("queuedCount"))
        else:
            fail("队列监控", json.dumps(queue, ensure_ascii=False))

        status, storage = call(base, "GET", "/ops/storage", token=admin_token)
        if isinstance(storage, dict) and storage.get("code") == 200:
            ok("存储监控", "used=%s, available=%s" %
               (storage["data"].get("usedBytes"), storage["data"].get("availableBytes")))
        else:
            fail("存储监控", json.dumps(storage, ensure_ascii=False))
    else:
        print("[SKIP] 运维端检查（未提供可用的管理员账号）")

    print("-" * 60)
    print("通过: %s 项，失败: %s 项" % (len(PASSED), len(FAILED)))
    if FAILED:
        print("失败步骤: %s" % ", ".join(FAILED))
        return 1
    print("扩展冒烟结果: PASS")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print("[ERROR] %s" % exc)
        sys.exit(1)
