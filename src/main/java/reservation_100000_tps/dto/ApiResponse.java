package reservation_100000_tps.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 공통 API 응답 DTO
 *
 * 일관된 응답 형식을 제공하기 위한 래퍼 클래스
 *
 * @param <T> 응답 데이터 타입
 */
@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;

    /**
     * 성공 응답 생성
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 성공 응답 생성 (메시지 포함)
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    /**
     * 실패 응답 생성
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }

    /**
     * 실패 응답 생성 (데이터 포함)
     */
    public static <T> ApiResponse<T> error(T data, String message) {
        return new ApiResponse<>(false, data, message);
    }
}
