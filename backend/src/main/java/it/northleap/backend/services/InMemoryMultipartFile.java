package it.northleap.backend.services;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

record InMemoryMultipartFile(String name, String type, byte[] bytes) implements MultipartFile {
    @Override public String getName()               { return "file"; }
    @Override public String getOriginalFilename()   { return name; }
    @Override public String getContentType()        { return type; }
    @Override public boolean isEmpty()              { return bytes.length == 0; }
    @Override public long getSize()                 { return bytes.length; }
    @Override public byte[] getBytes()              { return bytes; }
    @Override public InputStream getInputStream()   { return new ByteArrayInputStream(bytes); }

    @Override
    public void transferTo(java.io.File dest) throws IOException {
        Files.write(Path.of(dest.getAbsolutePath()), bytes);
    }
}
