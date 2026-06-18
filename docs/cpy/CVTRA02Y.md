# CVTRA02Y — 공시그룹 이자율 레코드 레이아웃

- **유형**: Copybook (VSAM KSDS 레코드 레이아웃 정의)
- **한 줄 요약**: 계정 그룹·거래 유형·거래 카테고리 조합에 대응하는 연이율(APR)을 저장하는 공시그룹(Disclosure Group) VSAM KSDS 파일의 50바이트 고정 레코드 구조를 정의한다.

---

## 기능 설명

`CVTRA02Y`는 VSAM KSDS 파일 `AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS`의 물리 레코드 레이아웃을 기술하는 공유 copybook이다. 이 파일은 이자 계산 배치(`CBACT04C`)가 월 이자를 산출할 때 조회하는 **이자율 참조 테이블** 역할을 한다.

레코드의 첫 16바이트는 KSDS 키(`DIS-GROUP-KEY`)이며, 나머지는 실제 연이율(`DIS-INT-RATE`) 6바이트와 예비 공간(`FILLER`) 28바이트로 구성된다. DISCGRP.jcl이 정의한 대로 `KEYS(16 0)`, `RECORDSIZE(50 50)`이 적용된다(소스: DISCGRP.jcl 라인 40–41).

조회 로직(CBACT04C 라인 416–459):
1. 해당 계정그룹·거래유형·거래카테고리 키로 RANDOM READ를 시도한다.
2. 키가 없으면(`DISCGRP-STATUS = '23'`) `FD-DIS-ACCT-GROUP-ID`를 `'DEFAULT'`로 치환해 폴백 레코드를 다시 읽는다.
3. 조회된 `DIS-INT-RATE`를 사용해 월 이자를 계산한다: `WS-MONTHLY-INT = (TRAN-CAT-BAL × DIS-INT-RATE) / 1200` (라인 464–465).

---

## 필드 레이아웃

소스 기준 총 50바이트 (`RECLN = 50`, 라인 2).

| 레벨 | 필드명 | PIC / USAGE | 바이트 | 의미 |
|------|--------|-------------|--------|------|
| 01 | `DIS-GROUP-RECORD` | — | 50 | 레코드 루트 그룹 |
| 05 | `DIS-GROUP-KEY` | — | 16 | **KSDS 기본 키** (16바이트 연속 구성) |
| 10 | `DIS-ACCT-GROUP-ID` | `PIC X(10)` | 10 | 계정 그룹 식별자 (예: `'DEFAULT'`는 폴백용). 알파뉴메릭 고정 10자. |
| 10 | `DIS-TRAN-TYPE-CD` | `PIC X(02)` | 2 | 거래 유형 코드 (예: `'PU'`=구매, `'PA'`=결제). 알파뉴메릭 2자. |
| 10 | `DIS-TRAN-CAT-CD` | `PIC 9(04)` | 4 | 거래 카테고리 코드. DISPLAY 숫자 4자리 (`0001`–`9999`). |
| 05 | `DIS-INT-RATE` | `PIC S9(04)V99` | 6 | **연이율(APR)**. 부호 있는 4자리 정수부 + 2자리 소수부. DISPLAY 형식(6바이트). 예: `001800` → 18.00%. |
| 05 | `FILLER` | `PIC X(28)` | 28 | 예비 공간. 향후 확장 또는 패딩용. |

**주요 데이터 표현 주의사항:**

- `DIS-INT-RATE`: `PIC S9(04)V99` DISPLAY 형식이다. `V`는 **묵시적 소수점**으로, 실제 저장 바이트에는 소수점 문자가 없다. 예를 들어 18.00%는 디스크에 `018000`(또는 부호 트레일링 EBCDIC `01800{`)으로 저장된다. `COMP-3`이 아니므로 6바이트 전부 EBCDIC 숫자 문자다. Java 변환 시 `BigDecimal`로 읽되 스케일을 2로 지정해야 한다.
- `DIS-TRAN-CAT-CD`: `PIC 9(04)` DISPLAY — 4바이트 EBCDIC 숫자. 연동 파일 `CVTRA04Y`의 카테고리 코드 체계와 일치해야 한다.
- `DIS-GROUP-KEY`는 3개 하위 필드의 물리적 연결(concatenation)이며, KSDS는 이 16바이트 전체를 단일 키로 취급한다.

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — 이 copybook은 다른 copybook을 COPY하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음 — copybook이므로 직접 호출되지 않으며, 아래 프로그램이 `COPY CVTRA02Y`로 포함한다.
  - `app/cbl/CBACT04C.cbl` (라인 107): 이자 계산 배치. `DIS-GROUP-RECORD`를 WORKING-STORAGE에 선언해 KSDS READ INTO 대상으로 사용. `DIS-INT-RATE`로 월 이자 산출.
- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS` — VSAM KSDS, RECORDSIZE(50 50), KEYS(16 0). JCL DD명 `DISCGRP`. 정의: `app/jcl/DISCGRP.jcl`. INTCALC 배치에서 DD `DISCGRP`로 할당 (`app/jcl/INTCALC.jcl` 라인 35–36).
  - 시드 데이터 플랫파일: `AWS.M2.CARDDEMO.DISCGRP.PS` (DISCGRP.jcl STEP15에서 VSAM으로 REPRO).
- **트랜잭션 ID 또는 EXEC PGM**: 없음 — copybook은 독립 실행 단위가 아니다.

---

## Java/현대화 노트

### 1. Java 레코드 클래스 매핑

```java
import java.math.BigDecimal;

/**
 * CVTRA02Y — DIS-GROUP-RECORD (50 bytes, VSAM KSDS)
 * KEYS(16 0): disAcctGroupId(10) + disTranTypeCd(2) + disTranCatCd(4)
 */
public class DisGroupRecord {

    // DIS-GROUP-KEY (16 bytes composite key)
    private String disAcctGroupId;   // PIC X(10)  — 계정 그룹 ID ("DEFAULT" = 폴백)
    private String disTranTypeCd;    // PIC X(02)  — 거래 유형 코드
    private int    disTranCatCd;     // PIC 9(04)  — 거래 카테고리 코드

    // DIS-INT-RATE: PIC S9(04)V99 DISPLAY → BigDecimal(scale=2)
    // 예: EBCDIC "018000" → new BigDecimal("180.00") → 연이율 180.00% 이 아니라
    //     V99가 소수점 2자리이므로 → BigDecimal("180.00").scaleByPowerOfTen(-2) = 1.8000 → 180.00%
    // 주의: COBOL에서 DIS-INT-RATE = 1800 이면 18.00%를 의미한다 (V99 → /100)
    private BigDecimal disIntRate;   // PIC S9(04)V99 → 소수점 2자리 BigDecimal

    // FILLER PIC X(28) — Java에서는 생략 (직렬화 시에만 패딩 필요)
}
```

### 2. PIC S9(04)V99 → BigDecimal 변환 시 함정

COBOL `PIC S9(04)V99`는 묵시적 소수점이므로 실제 값 `18.00%`는 내부적으로 정수 `1800`으로 저장된다. Java로 파싱할 때:

```java
// EBCDIC→ASCII 변환 후 6자리 숫자 문자열 예: "001800"
String raw = "001800";
BigDecimal rate = new BigDecimal(raw).scaleByPowerOfTen(-2);
// → 18.00  (즉 18.00% 연이율)

// CBACT04C 이자 계산 공식 재현 (라인 464-465):
// WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
BigDecimal monthlyInterest = tranCatBal.multiply(rate)
                                        .divide(new BigDecimal("1200"), 10, RoundingMode.HALF_UP);
```

`/1200`은 연이율을 월이율(1/12)로 환산하고 동시에 퍼센트를 소수로 변환(`÷100`)하는 단일 나눗셈이다. Java에서 `double`/`float`을 사용하면 금융 정밀도 오류가 발생하므로 반드시 `BigDecimal`을 사용해야 한다.

### 3. KSDS 폴백 키 패턴 → Java Optional/Repository 패턴

CBACT04C의 2단계 조회(특정 키 시도 → 실패 시 `'DEFAULT'` 키로 재시도)는 다음과 같이 구현할 수 있다:

```java
public DisGroupRecord findInterestRate(String acctGroupId, String tranTypeCd, int tranCatCd) {
    DisGroupKey key = new DisGroupKey(acctGroupId, tranTypeCd, tranCatCd);
    return repository.findById(key)
        .orElseGet(() -> repository.findById(
            new DisGroupKey("DEFAULT", tranTypeCd, tranCatCd))
            .orElseThrow(() -> new IllegalStateException(
                "DEFAULT disclosure group record not found")));
}
```

### 4. 복합 키 → JPA @EmbeddedId

KSDS 키 16바이트(계정그룹 10 + 거래유형 2 + 거래카테고리 4)는 JPA `@EmbeddedId`로 자연스럽게 매핑된다:

```java
@Embeddable
public class DisGroupKey implements Serializable {
    @Column(name = "DIS_ACCT_GROUP_ID", length = 10)
    private String disAcctGroupId;

    @Column(name = "DIS_TRAN_TYPE_CD", length = 2)
    private String disTranTypeCd;

    @Column(name = "DIS_TRAN_CAT_CD")
    private Integer disTranCatCd;
}
```

### 5. FILLER 28바이트 처리

현대화 시 `FILLER` 필드는 DB 컬럼으로 보존하거나 생략할 수 있다. 단, 레거시 시스템과 공존(co-existence) 단계에서 VSAM 파일에 다시 쓰는 경우에는 정확히 50바이트 레코드 길이를 유지해야 하므로 직렬화 레이어에서 28바이트 공백 패딩이 필요하다.

### 6. EBCDIC 주의 (배치 파일 마이그레이션)

`DIS-ACCT-GROUP-ID`(X), `DIS-TRAN-TYPE-CD`(X)는 EBCDIC 알파뉴메릭이고, `DIS-TRAN-CAT-CD`(9)는 EBCDIC 숫자다. AWS Mainframe Modernization 환경에서 기존 DISCGRP.PS 시드 파일을 로드할 때 EBCDIC→UTF-8 코드 변환이 필요하며, 숫자 필드의 존드 십진수(EBCDIC zoned decimal) 디코딩에 유의해야 한다.

---

*소스 버전: CardDemo_v1.0-15-g27d6c6f-68, 2022-07-19 (CVTRA02Y.cpy 라인 12)*
