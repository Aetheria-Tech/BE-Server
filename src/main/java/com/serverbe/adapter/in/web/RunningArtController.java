package com.serverbe.adapter.in.web;

import com.serverbe.adapter.in.web.dto.art.RunningArtResponse;
import com.serverbe.application.port.in.art.GetRunningArtUseCase;
import com.serverbe.application.port.in.art.DeleteRunningArtUseCase;
import com.serverbe.application.port.in.art.UpdateRunningArtUseCase;
import com.serverbe.adapter.in.web.dto.art.UpdateRunningArtRequest;
import com.serverbe.infrastructure.common.response.RestApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Running Art", description = "런닝 아트 관리 API")
@RestController
@RequestMapping("/api/v1/running-arts")
@RequiredArgsConstructor
public class RunningArtController {

    private final GetRunningArtUseCase getRunningArtUseCase;
    private final DeleteRunningArtUseCase deleteRunningArtUseCase;
    private final UpdateRunningArtUseCase updateRunningArtUseCase;

    @Operation(
            summary = "내 런닝 아트 목록 조회",
            description = "현재 로그인한 사용자가 생성한 모든 런닝 아트 목록을 최신순으로 조회합니다.",
            security = @SecurityRequirement(name = "jwtAuth"), // JWT 인증이 필요함을 명시
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공",
                            useReturnTypeSchema = true
                    ),
                    @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
                    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
            }
    )
    @GetMapping("/me")
    public RestApiResponse<Page<RunningArtResponse>> getByUserId(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @ParameterObject Pageable pageable
    ) {
        return RestApiResponse.success(getRunningArtUseCase.getRunningArtsByUserId(userId, pageable)
                .map(RunningArtResponse::toResponse));
    }

    @Operation(
            summary = "런닝 아트 단건 상세 조회",
            description = "런닝 아트의 고유 ID를 사용하여 상세 정보(제목, 내용, Polyline 경로 등)를 조회합니다.",
            security = @SecurityRequirement(name = "jwtAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공",
                            useReturnTypeSchema = true
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "해당 ID의 런닝 아트를 찾을 수 없음",
                            content = @Content(schema = @Schema(implementation = RestApiResponse.class))
                    ),
                    @ApiResponse(responseCode = "401", description = "인증 실패")
            }
    )
    @GetMapping("/{runningArtId}")
    public RestApiResponse<RunningArtResponse> getById(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "조회할 런닝 아트 ID", example = "1", required = true)
            @PathVariable(name = "runningArtId") Long runningArtId
    ) {
        return RestApiResponse.success(RunningArtResponse.toResponse(getRunningArtUseCase.getRunningArtById(userId, runningArtId)));
    }

    @Operation(
            summary = "런닝 아트 수정",
            description = "런닝 아트의 제목과 상세 내용을 수정합니다. 작성자 본인만 수정 가능합니다.",
            security = @SecurityRequirement(name = "jwtAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "수정 성공 (데이터 반환 없음)",
                            useReturnTypeSchema = true
                    ),
                    @ApiResponse(responseCode = "400", description = "입력 데이터 유효성 검증 실패"),
                    @ApiResponse(responseCode = "401", description = "인증 실패"),
                    @ApiResponse(responseCode = "403", description = "수정 권한 없음 (작성자 아님)"),
                    @ApiResponse(responseCode = "404", description = "수정할 런닝 아트를 찾을 수 없음")
            }
    )
    @PatchMapping("/{runningArtId}")
    public RestApiResponse<Void> update(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "수정할 런닝 아트 ID", example = "1", required = true)
            @PathVariable(name = "runningArtId") Long runningArtId,
            @RequestBody @Valid UpdateRunningArtRequest request
    ) {
        updateRunningArtUseCase.updateRunningArt(userId, runningArtId, request.toCommand());
        return RestApiResponse.noContent();
    }

    @Operation(
            summary = "런닝 아트 삭제",
            description = "런닝 아트의 고유 ID를 사용하여 리소스를 삭제합니다. 작성자 본인만 삭제할 수 있습니다.",
            security = @SecurityRequirement(name = "jwtAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "삭제 성공 (데이터 반환 없음)"
                    ),
                    @ApiResponse(responseCode = "401", description = "인증 실패"),
                    @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (작성자 아님)"),
                    @ApiResponse(responseCode = "404", description = "삭제할 런닝 아트를 찾을 수 없음"),
                    @ApiResponse(responseCode = "500", description = "서버 오류")
            }
    )
    @DeleteMapping("/{runningArtId}")
    public RestApiResponse<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "삭제할 런닝 아트 ID", example = "1", required = true)
            @PathVariable(name = "runningArtId") Long runningArtId
    ) {
        deleteRunningArtUseCase.deleteRunningArt(userId, runningArtId);
        return RestApiResponse.noContent();
    }

    @Operation(
            summary = "내 모든 런닝 아트 삭제",
            description = "현재 로그인한 사용자가 작성한 모든 런닝 아트 데이터를 일괄 삭제합니다. 이 작업은 되돌릴 수 없으니 주의하십시오.",
            security = @SecurityRequirement(name = "jwtAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "일괄 삭제 성공 (데이터 반환 없음)"
                    ),
                    @ApiResponse(responseCode = "401", description = "인증 실패"),
                    @ApiResponse(responseCode = "500", description = "서버 내부 오류 (삭제 작업 실패)")
            }
    )
    @DeleteMapping("/me")
    public RestApiResponse<Void> deleteAllByUser(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        deleteRunningArtUseCase.deleteAllRunningArtsByUserId(userId);
        return RestApiResponse.noContent();
    }

    @Operation(
            summary = "샘플로 등록된 Polyline이 적용된 GPX 파일 조회",
            description = "샘플이며 데이터베이스를 조회하거나 로그인이 필요하지 않습니다",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "GPX 파일 조회 성공"
                    )
            }
    )
    @GetMapping("/sample")
    public RestApiResponse<String> polylineGpx() {
        return RestApiResponse.success(
                "q`jdFub_fW@B?BAD?D?F?FADAD?F?D?F?D?F?D?F?F?F?FAF?F?D?F?F?F?F?FAF?F?F?F?F?FAD?F?F?F?F?F?FAF?H?FAF?F?HAF?F?FAF?F?F?FADAFCDAFADCD?F?F@D?D@H?HCF?BC@E@EAC?E?E?E?C?E?E?E@E@C@ABAD?DADAD?DADAD?FADCB?DAD?D?D?D?DAD?D?H?F?D?F?F?D?F?DAF?D?F?F?F?F?F?F@FAF@F?F?FEBGBIBE?G@CBEAEAE@E@E@E?E@E@E@E?EBE@GDE?E@E@E@CBCBCBEBC@E@C@E?CAG?C?E@EBE@E@E@E@G?CAEAE?E@GBEBE@EBAFCDE@CBEBEBEFEBG@EBEBEBC@E@EBE@CBE?E?E@G@E@EDCDGBG@G@E?E@E?E?E@C@E?E@E@EBCDEBC@EBE?E@E?C?E?E?C@E@C@AACC?CAE@E?E@G?E?EAE?GAGGE?GBGHCHAFAFCDADAFAFCF?FAF?FAF?F?DAFAD?BCBADCDD@H@F@H@F@H?F@F?FBFAFE@G@G@E@E@E?E?EAE?E?E@EBEBEBEBE@EBE?G@E@E@E@G@E?ECCC?G?G@E?G?G?G@G?GAG@G?G@I@G?I?G@GAG?E@I@G?G?I?I?G?G?IAEAG?IAG?G@GBG?G?I@G@G@G@I?G?G@G@G?G@I?G@I?G?I?ECEE?E?G@E?E@G?E@E@E?EAE@E?E?E@E?E?EAEAE?E@E@E?G@E?E?E@E@E@E?E?E?E?E@G@EAG?G?E?G@E@G@EAG?E?E?G?ECG@E?G@EAGAGBG@G@E?G?E?G@EAG?G?E@G?G?G@E?E?E?G?G?I?G?G@EAE@GAG?E?G?C?G?E?E@E@G?C?EAE?G?E?E?E?C?E?E?E?E?G?G@GAGBG?E?CAE?E?G?E@C?CK?E?G?GAEAGCEAEAGAGE?EAG?E?G?G?EAG?CCCEAGAG?GAE?GAG?GAG?GAGAGAGAG?GAEAG?GAG?E?G?ECG?GAG?GCC?IAGAGAIAGAGAGAG?G?E?GAGAGAG?I?GAG?G?G?I?G?I?GAI?G?I?G?IAGAG?IAGCI?GCGAG@G?G@GAG?I?GAE?I?I@I@G?I@GBI@I?I@G?G?G@G?I@G@IBG@I?I@I@G@G@I@G?G@I@G@G@G@G?GBGBGBG@G?G@I?I@I?G@I?G?I?G?G?G@I@G@G@GCIAI@GBG?G?IBG@IDKFMEIEEECCEGCE?GBCDG?E?E?I?GAECGCEGCIAI?G?I@I?I?G?G?G?I?G?G@G?I?G?I@I?G@G@G@G?G?G?GAI?G?I?I@G?IAGAI?I@I@G@ICG?G?I@KAI@I@I?G@EDEFEDCF?DAFCF?D@FAD?F?F?FCDCF@H?F@F?F@DAF?F@D?D?D?DAD?F@D?F?FAFCDEHAD@F?D@FAFBDDBHF@FADFB?DBHDFDDHBFBJDFBJBDDDDBBBB@BBB@@@B@DBDBD@@B@@B@@@A@@?@GDKFCBBD@BEDCBCBGBEBEBEDCFEDEHAD?HADCDADCFCDADCDCFCDADCDCDADEDADCDEDCDCDCBCDCDCFAFCDEFCDCFCDCDCDEBEFCDCDCDEBCBCB?D?D@D@D?F?D@F@D@D@DBDBFB?MJFBADADAD?D?D@D?D?F?DAD@F?D@D@D@D?D?D@D?D?D?F?D?F?FAD?BBDADAD?FADEDAFCDADABADAF?DCDADADAFAFADADADAFADAF?DADAFAD?DCFCDADADAFCDAF?DCFADAFAF?D?D@D?DAF?D?FAD?FAD?D?FADADAFADADADAFCDAFADAFADCFADCFCFADCDCFADCDCDCFADCF?DEDCDEBEBI@G@G@E@G@G@E?G?GAIAECCCEAECEAEAE?GAEAGCGAGCGCGAGEGCGAGAGCI?GAGAGCEAEEEAGCEAIAIAIAGCGCGCEACCEAECCCCAIAECGCGCGEGCICGCGCECIAEAGCEAGCGEGAGCGCGCEAI?GAEAECEAECCCEEECECCCCAA??@?@@?A?ACEEECGCEEEAAAA?E?E?E?CAEEGCECGCECCCECGAGCECECGAG?IAIAI?GAGAGAGAI?GAGAI?GAI?IAGCI?G?I?GAI?I?G?IAIAGAI?G?G?I?I@GAI?I?IAG?I?G?IAI?G?I?I@I?G@I@I?G@GAG@GAI@IEEG?EAGBE?E?GAE?E?EAG@E@GAE@G?G?E@G?E@G?E?E?E?E@E?EAE@C?A?E?C@E?E?E@E@GAE?E@G?EAGACAE?E?A?CAC?E@E?GAE?EAE?C@EAECEAEAE?EAGAEAEAE?CAE?G@E?E?G@E?EAG?EAG@E?G@G@G?E?G@G?E?G@G?E?E@CEDEF?F?D?F?F?FADAF?DAD?F?F@D?F?F?F@D?FBF?FAF?D@DADAD?DADAF@D?F?FAF?F@F@DBD@D?F?D?F@F?F?F?FAD?FAD?FADAFAD?F?DAFCDAF?F?F?F?F?F@F?D?FAF?DCDCDAF?D@F?DADEDCBCBGBG@G@G@I?G?I@G?I?G?I?GJEDADAB@DCDCDGBGBEDGDEBGDCFCFAFAFADAF?FAF?FADCDAHADAF?FAFAFCFCFADAFCDCFADCFCFADAFAD?FADAFAFADAFAFAFADAFCDAFAFADAFAFAFAF?FCFADCFADABCD?DCDAFADCFCHCFAF?H?FADCF?FADCFAFCFADAFADAD?FAD?DAFCBCAEAGBK?IAICECG?IAI?GAGAGAEAIAG?GCE?GAIAI?GBG@EDCBCDCDEDCFCDEDCDEDCDCBAFABCDCDEDEDCDEDCBEDABED?DCDAFCBADCBEDCDCDAFCBEDCDCDEDADEDCFABEDCBCDCDABCBCDCDCDEDCDADCFABAD?DA@?@AB?@DA?@?BCDEBGDEDEDIBG@EDEBCDCDEBEDGDEBEBG@E@C@E@G@G@G@I?G?I?G?I?G@I@I@G@GDGBG@GDEBGDCDEDCFCDEDCFCDCDCDCFCDAFAFCDCFADADAFCFADAFADAFADAFADAF?DAD?FADCDADCDAB?B?H@D@FADADAF?D?DCBEDCBEDAFAD?FAD?F?DAF?F?FAD?F?F?F?DADAF?DCB@@ABBBBF?DB@BB@FDD@D?B?F?FCFGHGFEBEBEDCBAJGLKHAD@BFBJFBHBHD@F@D@F?H?F?H?H@F?HBHFD?FCL@F???B@D?D?F?F?F?F@F@FAD@F?H?H@J@H?FAD@F?JAF?H@FAF@H?H?H?H@F@F?F?J@H?H?F@FAF@HAF@H?B?F?H?F?F@D?B?D?D@H?H@F@F?H?F@FFBGHC?BF@H?F?F?H?F?H?F?H@F?F?F?H?H@H?H?H@H?F?F?H@H?F@H?H?HBFBF@HBF?F@F?H?F@FAH?H?F?F@F?H?H?F?J@F@F?H@F?F?F@F?F?F@F?F?F@FBD?F?F?F?H?H?F?F?F@D?F?F@F@F?H@F?F?H@F?F?F@F?F?F?H?F?H?F?F?F?H?H?F?FAF?H?F?F?F@H?FAF?F?FAF?F?F@F?FAFA@?ACAG?GAE?G?G@E?E?EAE?E@E?E?E?E?GCEAEAE?E?GAE@E?G?GAE?E?GAC?EBE@EBI?IAGCEAEAG?EBGAGCGAGAEAGCI?GAEACH@??J?@ADAF?D?DAF?F?FCDCDCDCHCFCF?H@H?F@H?F@H?FAH?HAF?H?F?H@H?F?F?F?F?H?H?H?H?H?H?H@H?F?HAH?F@H?H?FAH?H@JCFAH@HAF?HCF@H?H?FAH@F@F?F?F?F?H@H@F@H?H?H?F?H?H@HAFAH?HCF?H?FAH?FAF?H@H@HAF?H?H?H?FAH?F@F@F@H@F@FAH?HAFAF@FD@D@F@D?D@FAD@F@DBDDD@DBDBFAFDDAF@D?F?F@FAD@FBDAH@F?FCD@FBD@DAD?B@D@F@F@F@F?F@F@DBFA?H?F?HAH?H?JAHAHAFAHCFCDCB@H?D?H@DAFAH?F@JAD?FAD?F?HAH?F?H?F?F?F?H?J?F?F?H?FAFAFCFAB?FCD?F?F?F?F?F?H?H?H?F?F@F?FAD@@?D?HAFAFCFAF?DAFAF@FAF?DADEBEBALADAFA@E@DDBD?DADCDABCFAF?HAF?D@FBDDDDJHB@HCHAFAFAFAF@HAF?F?H?FAFAF?H?HAH?HAFAH?HAFAHAFAHAH?H?FAFAJAF?H@H@H?H?F?F?HCFG@EBE?E@G?EAE?GAE@G?G?E@E@E?E@E?C?G?G@G@G?G@G?E?G?E@EBEDEDCDCDA@EEE?G@E@E?EAG?G?ECEAG?IBG?I?G@GCGAGAEAGAC@CBABAGAKECE@E@GAG?G?IAE?EBG@G?G?G?G?G@E?G?G@E?E@EAE?E@E?G@E?GAE?EAG?E@G@G?G?E?G?EAG?EBGBG@G@GCG?E@EAE@EAG@E?EAG@E@G@G?E?GBG?G@E?G?E@G@G?GAE?G?G?GAE?G?G?G@EAG@GAGAG?G?E?GCGAG?G?G?GAE?G?G?EAGAE?G@G?G@E@GAG?G?G?GBE?G@E@G@G?E@G?E?GAEAG?G?E?GAEAE?GDCBEBEDCDGBCBEDEBABCBA@??A?AFAFADCBEBGBEBCBEDCBCDEBEBEBE@G@CDCBAF?HAF?DAD"
        );
    }
}