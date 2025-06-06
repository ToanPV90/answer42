package com.samjdtechnologies.answer42.util;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtil {

    public static void copyLarge(InputStream in, Path target) throws IOException {
    try (OutputStream out = Files.newOutputStream(target)) {
        byte[] buf = new byte[64 * 1024];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
    }
}

    
}
