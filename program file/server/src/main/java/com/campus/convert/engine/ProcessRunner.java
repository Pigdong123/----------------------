package com.campus.convert.engine;

import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 外部转换进程统一执行器：超时控制、强制回收、错误信息截断。
 * <p>
 * 审计新增：三个引擎此前各自 TODO，且直接拼接命令。此处统一封装，
 * 避免超时进程泄漏与错误信息缺失。
 */
public final class ProcessRunner {

    private static final int MAX_ERROR_CHARS = 800;

    private ProcessRunner() {
    }

    public static void run(List<String> command, int timeoutMinutes, String engineName) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = null;
        try {
            process = processBuilder.start();
            boolean finished = process.waitFor(Math.max(1, timeoutMinutes), TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new BizException(ResultCode.TASK_CONVERT_FAILED,
                        engineName + "转换超时（>" + timeoutMinutes + " 分钟）");
            }
            int exit = process.exitValue();
            if (exit != 0) {
                String detail = readOutput(process);
                throw new BizException(ResultCode.TASK_CONVERT_FAILED,
                        engineName + "转换失败(exit=" + exit + "): " + abbreviate(detail));
            }
        } catch (IOException e) {
            throw new BizException(ResultCode.TASK_CONVERT_FAILED,
                    engineName + "无法启动: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw new BizException(ResultCode.TASK_CONVERT_FAILED, engineName + "转换被中断");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 执行命令并捕获标准输出（用于 OCR 等需要返回文本的引擎）。
     */
    public static String runAndCapture(List<String> command, int timeoutMinutes, String engineName) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = null;
        try {
            process = processBuilder.start();
            Process running = process;
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readStream(running.getInputStream()));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(running.getErrorStream()));

            boolean finished = process.waitFor(Math.max(1, timeoutMinutes), TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new BizException(ResultCode.TASK_CONVERT_FAILED,
                        engineName + "执行超时（>" + timeoutMinutes + " 分钟）");
            }
            String out = stdout.join();
            String err = stderr.join();
            if (process.exitValue() != 0) {
                throw new BizException(ResultCode.TASK_CONVERT_FAILED,
                        engineName + "执行失败(exit=" + process.exitValue() + "): " + abbreviate(err));
            }
            return out;
        } catch (IOException e) {
            throw new BizException(ResultCode.TASK_CONVERT_FAILED,
                    engineName + "无法启动: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw new BizException(ResultCode.TASK_CONVERT_FAILED, engineName + "执行被中断");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String readStream(InputStream in) {
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String readOutput(Process process) {
        try (InputStream in = process.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= MAX_ERROR_CHARS ? trimmed : trimmed.substring(0, MAX_ERROR_CHARS) + "...";
    }
}
