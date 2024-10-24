package com.test.phone.service;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;

public class ImageUtils {

    // 유효한 이미지 파일인지 확인하는 메서드
    public static boolean isValidImageFile(File imageFile) {
        // 파일 형식 확인
        String fileName = imageFile.getName();
        String[] validExtensions = {".jpg", ".jpeg", ".png", ".gif"};
        boolean hasValidExtension = false;

        for (String extension : validExtensions) {
            if (fileName.toLowerCase().endsWith(extension)) {
                hasValidExtension = true;
                break;
            }
        }

        if (!hasValidExtension) {
            return false; // 유효하지 않은 확장자
        }

        // 파일 크기 확인 (예: 5MB 이하)
        if (imageFile.length() > 5 * 1024 * 1024) {
            return false; // 파일 크기 초과
        }

        // 파일 내용 확인
        try (ImageInputStream input = ImageIO.createImageInputStream(imageFile)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            return readers.hasNext(); // 유효한 이미지 리더가 있는지 확인
        } catch (IOException e) {
            return false; // IO 예외 발생 시 유효하지 않은 파일로 간주
        }
    }
}

