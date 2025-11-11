package com.example.assistapp.model

// OCR 응답의 최상위 구조
data class OcrResponseData(
    val status: String?, // "SUCCESS", "FAIL"
    val data: OcrData?
)

// 실제 OCR 데이터
data class OcrData(
    val billType: String?, // "E" (전자) or "P" (종이)
    val total: OcrTotal?,
    val items: List<OcrItem>?
)

// "total" 객체
data class OcrTotal(
    val count: Int?, // items 리스트의 길이
    val totalPrice: PriceInfo? // 합계 금액 정보
)

// 개별 "items" 항목
data class OcrItem(
    val name: String?, // 예: "T-shirts"
    val count: Int?, // 예: 1
    val unitPrice: PriceInfo?, // 단가
    val price: PriceInfo? // 수량 * 단가
)

// 가격 정보 (unitPrice, price, totalPrice에서 공통 사용)
data class PriceInfo(
    val text: String?, // 예: "1,000"
    val value: Int?, // 예: 1000
    val confidence: Double?, // 신뢰도 (0.0 ~ 1.0)
    val box: List<Int>? // [x1, y1, x2, y2]
)