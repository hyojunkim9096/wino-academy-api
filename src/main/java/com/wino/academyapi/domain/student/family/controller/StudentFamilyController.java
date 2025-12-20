package com.wino.academyapi.domain.student.family.controller;

import com.wino.academyapi.domain.student.family.dto.StudentGuardianLinkDtos.LinkCreateRequest;
import com.wino.academyapi.domain.student.family.dto.StudentGuardianLinkDtos.LinkUpdateRequest;
import com.wino.academyapi.domain.student.family.dto.GuardianLinkSummary;
import com.wino.academyapi.domain.student.family.service.StudentFamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/students/{studentId}/guardians")
@RequiredArgsConstructor
public class StudentFamilyController {

    private final StudentFamilyService service;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<GuardianLinkSummary>> list(@PathVariable Long studentId) {
        return ResponseEntity.ok(service.listGuardiansByStudent(studentId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Long> add(@PathVariable Long studentId, @RequestBody LinkCreateRequest req) {
        req.setStudentId(studentId);
        Long linkId = service.createLink(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(linkId);
    }

    @PutMapping(value = "/{linkId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(@PathVariable Long studentId,
                                       @PathVariable Long linkId,
                                       @RequestBody LinkUpdateRequest req) {
        service.updateLink(linkId, req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{linkId}")
    public ResponseEntity<Void> remove(@PathVariable Long studentId, @PathVariable Long linkId) {
        service.deleteLink(linkId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync")
    public ResponseEntity<Void> syncFamily(@PathVariable Long studentId) {
        service.syncFamily(studentId);
        return ResponseEntity.ok().build();
    }
}