# CVTRA04Y — 거래 카테고리 레코드 레이아웃

- **유형**: Copybook
- **한 줄 요약**: 거래 유형 코드(TYPE-CD)와 카테고리 코드(CAT-CD)의 조합을 기본 키로 하여, 해당 카테고리에 대한 50자 설명 텍스트를 보유하는 참조(lookup) 레코드 구조를 정의한다. VSAM KSDS 파일 TRANCATG(레코드 길이 60바이트)의 레코드 레이아웃이다.

---

## 기능 설명

CVTRA04Y는 CardDemo 시스템에서 거래 카테고리 마스터 데이터를 표현하는 copybook이다.

거래는 두 차원의 분류 체계를 가진다.

- **거래 유형(TRAN-TYPE-CD)**: 2자리 알파벳 코드. 예: `PR`(구매), `RF`(환불) 등. 별도 마스터 파일 TRANTYPE의 copybook `CVTRA03Y`에 유형 설명이 정의된다.
- **거래 카테고리(TRAN-CAT-CD)**: 4자리 숫자 코드. 유형 내의 세부 분류를 나타낸다.

두 코드의 조합(복합 키)으로 TRANCATG VSAM 파일에서 레코드를 유일하게 식별하며, `TRAN-CAT-TYPE-DESC`가 해당 조합에 대한 사람이 읽을 수 있는 설명을 제공한다.

이 copybook은 주로 배치 리포트 프로그램(CBTRN03C 등)과 온라인 거래 입력 프로그램에서 카테고리 유효성 검증 및 설명 출력을 위해 사용된다(에이전트 메모리 `copybook_record_layouts.md` 참조: `TRANCATG(60) ↔ CVTRA04Y`).

---

## 필드 레이아웃

소스 기준 전체 레코드 길이: **60바이트** (주석 `RECLN = 60` 명시, 1행)

| 레벨 | 필드명 | PIC / USAGE | 바이트 수 | 의미 |
|------|--------|-------------|-----------|------|
| 01 | `TRAN-CAT-RECORD` | — | 60 | 루트 레코드 그룹 |
| 05 | `TRAN-CAT-KEY` | — | 6 | VSAM KSDS 복합 키 그룹 |
| 10 | `TRAN-TYPE-CD` | `PIC X(02)` | 2 | 거래 유형 코드 (알파뉴메릭 2자리) |
| 10 | `TRAN-CAT-CD` | `PIC 9(04)` | 4 | 거래 카테고리 코드 (숫자 4자리, DISPLAY 형식) |
| 05 | `TRAN-CAT-TYPE-DESC` | `PIC X(50)` | 50 | 카테고리 설명 문자열 (고정 50자, 공백 패딩) |
| 05 | `FILLER` | `PIC X(04)` | 4 | 예약 공간 (현재 미사용, 향후 확장 여지) |

**바이트 합산 검증**: TRAN-TYPE-CD(2) + TRAN-CAT-CD(4) + TRAN-CAT-TYPE-DESC(50) + FILLER(4) = **60바이트** — 주석 `RECLN = 60`과 일치 (소스 1행).

**주의 사항**:

- `TRAN-CAT-CD`는 `PIC 9(04)` DISPLAY 형식이다. COMP-3(packed decimal)이나 COMP(binary)가 아니므로 EBCDIC 환경에서 4바이트를 그대로 소비한다. ASCII/Java 환경에서는 단순 `String`(또는 `int`)으로 읽을 수 있다.
- 88-level 조건명, `REDEFINES`, `OCCURS`, `COMP-3` 구문은 이 copybook에 존재하지 않는다.
- `TRAN-CAT-KEY` 그룹 필드(05레벨)는 KSDS의 키 길이 6바이트를 정의하기 위한 논리적 그룹화이며, 실제 VSAM 파일 정의(DD/FD)에서 KEY LENGTH(6)로 참조된다(추측: 별도 JCL/FD 확인 필요).

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — CVTRA04Y 내부에 `COPY` 문 없음
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**: VSAM KSDS 파일 `TRANCATG` (레코드 길이 60바이트, 키: `TRAN-CAT-KEY` 6바이트 오프셋 0). 이 파일을 읽는 주요 프로그램은 CBTRN03C(거래 상세 리포트 배치, 에이전트 메모리 `transaction_report_cbtrn03c.md`)이다.
- **트랜잭션 ID 또는 EXEC PGM**: 없음

---

## Java/현대화 노트

### 1. Java DTO 매핑

```java
/**
 * CVTRA04Y TRAN-CAT-RECORD 에 해당하는 Java 레코드.
 * VSAM TRANCATG 파일의 단일 레코드(60바이트)를 표현한다.
 */
public record TranCatRecord(
    String tranTypeCd,       // TRAN-TYPE-CD  PIC X(02) — 거래 유형 코드
    String tranCatCd,        // TRAN-CAT-CD   PIC 9(04) — 4자리 숫자 문자열 또는 int
    String tranCatTypeDesc   // TRAN-CAT-TYPE-DESC PIC X(50) — 설명 (trim 권장)
) {
    /** VSAM 복합 키에 해당하는 복합 키 값 객체 */
    public record Key(String tranTypeCd, String tranCatCd) {}

    public Key key() {
        return new Key(tranTypeCd, tranCatCd);
    }
}
```

### 2. 복합 키 처리

COBOL에서 `TRAN-CAT-KEY`(05레벨 그룹)는 VSAM KSDS의 물리적 키 범위를 나타낸다. Java에서는 `Map<TranCatRecord.Key, TranCatRecord>`를 사용하거나, JPA `@EmbeddedId`로 복합 기본 키를 표현할 수 있다.

```java
@Embeddable
public class TranCatId implements Serializable {
    private String tranTypeCd;   // 2자
    private String tranCatCd;    // 4자리 숫자 문자열
}
```

### 3. 고정 길이 문자열 처리 주의점

- `TRAN-CAT-TYPE-DESC PIC X(50)`은 EBCDIC 환경에서 항상 50바이트 고정 길이로 저장된다. 짧은 설명은 공백으로 오른쪽 패딩된다.
- Java로 읽을 때는 반드시 `.trim()` 또는 `StringUtils.trimRight()`를 적용해야 불필요한 공백이 UI나 비교 로직에 영향을 주지 않는다.
- EBCDIC↔ASCII 변환 시 `Charset.forName("IBM037")` 또는 `CP1047`로 디코딩해야 한다. `PIC 9(04)` DISPLAY 필드도 EBCDIC 숫자 문자(0xF0~0xF9)로 저장되어 있으므로 변환 후 `Integer.parseInt()`로 파싱한다.

### 4. FILLER 처리

`FILLER PIC X(04)`(소스 9행)는 Java DTO에서 무시해도 무방하다. 단, 고정 길이 바이너리 파일을 파싱할 때는 오프셋 계산을 위해 4바이트를 건너뛰어야 한다(56번째 바이트부터 4바이트).

### 5. `TRAN-CAT-CD` 타입 선택

`PIC 9(04)` DISPLAY이므로 Java에서 `String`(4자리 zero-padded)과 `int` 중 선택이 필요하다. 코드 값을 사람이 읽는 식별자로 쓰는 경우(예: `"0050"`)라면 `String`을 유지하는 편이 안전하다. 앞자리 0이 의미를 가질 수 있기 때문이다.

### 6. 참조 데이터(Lookup) 전략

TRANCATG는 변경이 드문 참조 테이블이다. 현대화 시 애플리케이션 시작 시점에 전체 레코드를 `Map<Key, TranCatRecord>`로 로드하는 인메모리 캐시 전략이 적합하다. VSAM 파일을 RDBMS로 전환한다면 `tran_cat` 테이블로 매핑하고, 배치에서는 MyBatis/JPA로 조회한다.
