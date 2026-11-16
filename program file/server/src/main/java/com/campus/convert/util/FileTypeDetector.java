package com.campus.convert.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public final class FileTypeDetector {

    private static final List<byte[]> PNG_SIGNATURE = List.of(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    private static final List<byte[]> JPEG_SIGNATURE = List.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    private static final List<byte[]> FLAC_SIGNATURE = List.of("fLaC".getBytes());
    private static final List<byte[]> RIFF_SIGNATURE = List.of("RIFF".getBytes());
    private static final List<byte[]> NCM_SIGNATURE = List.of("CTENFDAM".getBytes());
    private static final List<byte[]> DJVU_SIGNATURE = List.of("AT&TFORM".getBytes());
    private static final List<byte[]> PDF_SIGNATURE = List.of("%PDF".getBytes());
    private static final List<byte[]> OGG_SIGNATURE = List.of("OggS".getBytes());
    private static final List<byte[]> MP3_ID3_SIGNATURE = List.of("ID3".getBytes());
    private static final List<String> HEIC_BRANDS = List.of("heic", "heix", "mif1", "msf1", "hevc", "hevx");

    private FileTypeDetector() {
    }

    /**
     * 通过文件魔数识别真实类型，防止扩展名伪造
     *
     * @return 识别到的扩展名（小写，不含点），无法识别返回 null
     */
    public static String detectExtension(MultipartFile file) throws IOException {
        byte[] header = readHeader(file);
        if (header == null || header.length < 4) {
            return null;
        }
        if (match(header, PNG_SIGNATURE)) {
            return "png";
        }
        if (match(header, JPEG_SIGNATURE)) {
            return "jpg";
        }
        if (match(header, FLAC_SIGNATURE)) {
            return "flac";
        }
        if (match(header, RIFF_SIGNATURE) && header.length >= 12 && "WAVE".equals(ascii(header, 8, 4))) {
            return "wav";
        }
        if (match(header, NCM_SIGNATURE)) {
            return "ncm";
        }
        if (match(header, DJVU_SIGNATURE)) {
            return "djvu";
        }
        if (match(header, PDF_SIGNATURE)) {
            return "pdf";
        }
        if (match(header, OGG_SIGNATURE)) {
            return "ogg";
        }
        if (match(header, MP3_ID3_SIGNATURE)) {
            return "mp3";
        }
        // MPEG 帧同步：0xFFEx
        if (header.length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0) {
            return "mp3";
        }
        // HEIC/HEIF：ftyp box + 品牌
        if (header.length >= 12 && "ftyp".equals(ascii(header, 4, 4))) {
            String brand = ascii(header, 8, 4).trim().toLowerCase();
            if (HEIC_BRANDS.contains(brand)) {
                return "heic";
            }
        }
        // 其余（docx/pptx/m4s/mov/pages 等）由扩展名兜底
        return null;
    }

    private static String ascii(byte[] data, int offset, int length) {
        return new String(data, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static byte[] readHeader(MultipartFile file) throws IOException {
        int headerSize = 16;
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[headerSize];
            int read = inputStream.read(header);
            if (read <= 0) {
                return null;
            }
            return read == headerSize ? header : Arrays.copyOf(header, read);
        }
    }

    private static boolean match(byte[] header, List<byte[]> signatures) {
        for (byte[] signature : signatures) {
            if (header.length >= signature.length && Arrays.equals(Arrays.copyOf(header, signature.length), signature)) {
                return true;
            }
        }
        return false;
    }
}
