package com.nas.webserver.controller;

import com.nas.webserver.model.FileMetadata;
import com.nas.webserver.service.FileMetadataService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/file-metadata")
public class FileMetadataController {
    private final FileMetadataService service;
    public FileMetadataController(FileMetadataService service) { this.service = service; }

    @GetMapping
    public List<FileMetadata> listMetadata() { return service.list(); }
}
