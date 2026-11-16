package com.campus.convert.engine.ncm;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * NCM（网易云音乐加密格式）解密器。
 * <p>
 * 结构：8 字节魔数 + 2 字节保留 + 密钥区 + 元数据区 + 4 字节 CRC + 封面区 + 音频流。
 * 音频流使用由密钥生成的 256 字节 S 盒进行异或解密。
 * <p>
 * 安全：所有长度字段均做上限校验，避免恶意文件导致内存耗尽。
 */
public final class NcmDecoder {

    private static final byte[] MAGIC = {'C', 'T', 'E', 'N', 'F', 'D', 'A', 'M'};
    private static final byte[] CORE_KEY = {
            0x68, 0x7A, 0x48, 0x52, 0x41, 0x6D, 0x73, 0x6F,
            0x35, 0x6B, 0x49, 0x6E, 0x62, 0x61, 0x78, 0x2B
    };
    private static final byte[] KEY_PREFIX = "neteasecloudmusic".getBytes(StandardCharsets.UTF_8);
    private static final int MAX_KEY_LEN = 1024;
    private static final int MAX_META_LEN = 1024 * 1024;
    private static final int MAX_IMAGE_LEN = 20 * 1024 * 1024;
    private static final int BUFFER_SIZE = 64 * 1024;

    private NcmDecoder() {
    }

    public record Result(Path file, String ext) {
    }

    public static Result decrypt(Path source, Path tempDir) throws IOException {
        try (InputStream raw = Files.newInputStream(source);
             BufferedInputStream in = new BufferedInputStream(raw)) {

            byte[] magic = readN(in, MAGIC.length);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("不是有效的 NCM 文件（魔数不匹配）");
            }
            skipFully(in, 2);

            int keyLen = readLittleEndianInt(in);
            if (keyLen <= 0 || keyLen > MAX_KEY_LEN) {
                throw new IOException("NCM 密钥长度非法: " + keyLen);
            }
            byte[] keyData = readN(in, keyLen);

            int metaLen = readLittleEndianInt(in);
            if (metaLen < 0 || metaLen > MAX_META_LEN) {
                throw new IOException("NCM 元数据长度非法: " + metaLen);
            }
            skipFully(in, metaLen);
            skipFully(in, 4); // CRC

            int imageLen = readLittleEndianInt(in);
            if (imageLen < 0 || imageLen > MAX_IMAGE_LEN) {
                throw new IOException("NCM 封面长度非法: " + imageLen);
            }
            skipFully(in, imageLen);

            byte[] realKey = deriveKey(keyData);
            int[] box = buildKeyBox(realKey);

            Path temp = Files.createTempFile(tempDir, "ncm-", ".bin");
            String ext = decryptStream(in, temp, box);
            return new Result(temp, ext);
        }
    }

    private static String decryptStream(InputStream in, Path temp, int[] box) throws IOException {
        int last = 0;
        long index = 0;
        String ext = null;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream out = Files.newOutputStream(temp)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                last = decryptChunk(buffer, read, box, index, last);
                if (ext == null) {
                    ext = detectExt(buffer, read);
                }
                out.write(buffer, 0, read);
                index += read;
            }
        }
        return ext == null ? "bin" : ext;
    }

    /**
     * 逐字节异或解密；返回跨块延续的 last 状态。
     */
    private static int decryptChunk(byte[] data, int length, int[] box, long index, int last) {
        for (int i = 0; i < length; i++) {
            int j = (int) ((index + 1) & 0xFF);
            last = (box[j] + last) & 0xFF;
            data[i] ^= (byte) box[(box[j] + last) & 0xFF];
            index++;
        }
        return last;
    }

    private static byte[] deriveKey(byte[] keyData) throws IOException {
        byte[] xored = new byte[keyData.length];
        for (int i = 0; i < keyData.length; i++) {
            xored[i] = (byte) (keyData[i] ^ 0x64);
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(CORE_KEY, "AES"));
            byte[] decrypted = cipher.doFinal(xored);

            int start = indexOf(decrypted, KEY_PREFIX);
            start = start < 0 ? 0 : start + KEY_PREFIX.length;
            int end = Math.min(decrypted.length, start + 16);
            byte[] key = Arrays.copyOfRange(decrypted, start, end);
            if (key.length != 16) {
                key = Arrays.copyOfRange(decrypted, Math.max(0, decrypted.length - 16), decrypted.length);
            }
            return key;
        } catch (Exception e) {
            throw new IOException("NCM 密钥解密失败: " + e.getMessage(), e);
        }
    }

    private static int[] buildKeyBox(byte[] key) {
        int[] box = new int[256];
        for (int i = 0; i < 256; i++) {
            box[i] = i;
        }
        int last = 0;
        for (int i = 0; i < 256; i++) {
            last = (box[i] + last + (key[i % key.length] & 0xFF)) & 0xFF;
            int tmp = box[i];
            box[i] = box[last];
            box[last] = tmp;
        }
        return box;
    }

    private static String detectExt(byte[] b, int n) {
        if (n >= 4 && b[0] == 'f' && b[1] == 'L' && b[2] == 'a' && b[3] == 'C') {
            return "flac";
        }
        if (n >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') {
            return "mp3";
        }
        if (n >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0) {
            return "mp3";
        }
        if (n >= 4 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S') {
            return "ogg";
        }
        if (n >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E') {
            return "wav";
        }
        return "bin";
    }

    private static int indexOf(byte[] data, byte[] target) {
        outer:
        for (int i = 0; i <= data.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (data[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int readLittleEndianInt(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        if ((b0 | b1 | b2 | b3) < 0) {
            throw new EOFException("NCM 文件不完整");
        }
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static byte[] readN(InputStream in, int length) throws IOException {
        byte[] data = in.readNBytes(length);
        if (data.length != length) {
            throw new EOFException("NCM 文件不完整");
        }
        return data;
    }

    private static void skipFully(InputStream in, long length) throws IOException {
        long remaining = length;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new EOFException("NCM 文件不完整");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }
}
