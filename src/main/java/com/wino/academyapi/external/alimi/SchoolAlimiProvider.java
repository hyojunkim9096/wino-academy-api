// src/main/java/com/wino/academyapi/external/alimi/SchoolAlimiProvider.java
package com.wino.academyapi.external.alimi;

import com.wino.academyapi.domain.school.entity.SchoolStage;
import com.wino.academyapi.domain.school.sync.SchoolSyncProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.external.school.schoolinfo", name = "enabled", havingValue = "true")
public class SchoolAlimiProvider implements SchoolSyncProvider {

    private final SchoolAlimiClient client;
    private final SchoolInfoProperties props;

    @Override public boolean isEnabled() { return props.isEnabled(); }

    /** 스케줄러 등에서 식별할 이름 — yml 설정(app.school.sync.provider)과 맞춤 */
    @Override public String name() { return "SCHOOLINFO"; }

    /**
     * scopes는 SchoolInfo API에서 직접적 의미가 없으므로 무시하고
     * 지정된 stages 기준으로 전국 수집을 수행.
     */
    @Override
    public FetchReport fetch(Set<String> scopes, Set<SchoolStage> stages, Consumer<RemoteSchool> sink) {
        FetchReport r = new FetchReport();
        Map<String,String> errors = new LinkedHashMap<>();
        int rows = 0;

        try {
            EnumSet<SchoolStage> st = (stages == null || stages.isEmpty())
                    ? EnumSet.of(SchoolStage.E, SchoolStage.M, SchoolStage.H)
                    : EnumSet.copyOf(stages);

            for (SchoolAlimiClient.SchoolDto dto : client.fetchNationwide(st)) {
                if (dto == null) continue;
                sink.accept(RemoteSchool.builder()
                        .externalCode(dto.getExternalCode())
                        .name(dto.getName())
                        .stage(dto.getStage())
                        .active(dto.getActive() != null ? dto.getActive() : Boolean.TRUE)
                        .eduOfficeCode(dto.getEduOfficeCode())
                        .postalCode(dto.getPostalCode())
                        .address(dto.getRoadAddress())
                        .detailAddress(dto.getJibunAddress())
                        .admCode(dto.getAdmCode())
                        .lat(dto.getLat())
                        .lng(dto.getLng())
                        .homepageUrl(dto.getHomepageUrl())
                        .phone(dto.getPhone())
                        .build());
                rows++;
            }

            r.setRows(rows);
            r.setTriedScopes(Set.of("<nationwide>"));
            r.setErrors(errors);
            return r;

        } catch (Exception e) {
            errors.put("<nationwide>", e.getMessage());
            r.setRows(rows);
            r.setErrors(errors);
            return r;
        }
    }
}
