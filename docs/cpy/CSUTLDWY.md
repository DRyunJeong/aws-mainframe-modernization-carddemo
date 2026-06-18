# CSUTLDWY — 날짜 편집·검증 작업영역 Copybook

- **유형**: Copybook (WORKING-STORAGE 삽입용)
- **한 줄 요약**: `CCYYMMDD` 형식 날짜를 문자/숫자 이중 뷰로 분해하고, 연·월·일 개별 유효성 플래그와 검증 결과 메시지 구조를 제공하는 날짜 유틸리티 작업영역 레이아웃.

---

## 기능 설명

이 copybook은 날짜 값을 다루는 모든 프로그램이 WORKING-STORAGE에 `COPY CSUTLDWY`로 삽입하여 사용하는 **공유 작업 버퍼**다. 크게 세 역할을 한다.

1. **날짜 분해 버퍼** (`WS-EDIT-DATE-CCYYMMDD`, 라인 4-36)
   CCYYMMDD 8자리 날짜를 CC·YY·MM·DD 네 조각으로 나눈다. 각 조각에 `REDEFINES`로 숫자 뷰를 동시에 제공하므로, 문자열 비교와 산술 연산을 같은 변수 공간에서 전환하며 쓸 수 있다. 월·일 조각에는 88-레벨 조건명이 붙어 있어 "31일 달인가?", "2월인가?" 같은 검증을 if-else 없이 표현한다.

2. **현재 날짜 버퍼** (`WS-CURRENT-DATE`, 라인 38-42)
   `FUNCTION CURRENT-DATE` 또는 CICS `EXEC CICS ASKTIME/FORMATTIME`의 결과를 담는 수신 영역이다. 마찬가지로 REDEFINES로 숫자 뷰를 병행 제공한다.

3. **검증 결과 영역** (`WS-EDIT-DATE-FLGS`, `WS-DATE-VALIDATION-RESULT`, 라인 43-85)
   연·월·일 각각의 유효/무효/공백 상태 플래그(`WS-EDIT-DATE-FLGS`)와, 심각도 코드·메시지 번호·결과 문자열·테스트 날짜·포맷 마스크를 한 줄로 이어 붙인 출력 레코드(`WS-DATE-VALIDATION-RESULT`)를 정의한다. 이 구조는 유틸리티 서브루틴(`CSUTLDTC` 추정)이 `CALL`로 검증 결과를 돌려쓰는 파라미터 블록으로 보인다.

> **(추측)** 주석("DATE related code")과 필드 구성 상 `CSUTLDTC`(날짜 변환·검증 서브루틴)의 LINKAGE SECTION과 쌍을 이루는 작업영역으로 설계된 것으로 추정된다. `CSUTLDTC` 소스가 확인되면 이 추측을 검증해야 한다.

---

## 필드 레이아웃

### 그룹 1 — 날짜 편집 버퍼 (`WS-EDIT-DATE-CCYYMMDD`, 8바이트)

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `WS-EDIT-DATE-CCYYMMDD` | 그룹 (8B) | 8 | CCYYMMDD 전체 날짜 |
| `WS-EDIT-DATE-CCYY` | 그룹 (4B) | 4 | 연도 4자리 |
| `WS-EDIT-DATE-CC` | `PIC X(2)` | 2 | 세기 부분 문자 뷰 |
| `WS-EDIT-DATE-CC-N` | `REDEFINES WS-EDIT-DATE-CC` `PIC 9(2)` | 2 | 세기 숫자 뷰 |
| ↳ `THIS-CENTURY` (88) | VALUE 20 | — | CC = 20 → 21세기 조건 |
| ↳ `LAST-CENTURY` (88) | VALUE 19 | — | CC = 19 → 20세기 조건 |
| `WS-EDIT-DATE-YY` | `PIC X(2)` | 2 | 연 하위 2자리 문자 뷰 |
| `WS-EDIT-DATE-YY-N` | `REDEFINES WS-EDIT-DATE-YY` `PIC 9(2)` | 2 | 연 하위 2자리 숫자 뷰 |
| `WS-EDIT-DATE-CCYY-N` | `REDEFINES WS-EDIT-DATE-CCYY` `PIC 9(4)` | 4 | 4자리 연도 숫자 뷰(CC+YY 합산) |
| `WS-EDIT-DATE-MM` | `PIC X(2)` | 2 | 월 문자 뷰 |
| `WS-EDIT-DATE-MM-N` | `REDEFINES WS-EDIT-DATE-MM` `PIC 9(2)` | 2 | 월 숫자 뷰 |
| ↳ `WS-VALID-MONTH` (88) | VALUES 1 THROUGH 12 | — | 월 유효 범위 조건 |
| ↳ `WS-31-DAY-MONTH` (88) | VALUES 1,3,5,7,8,10,12 | — | 31일 달 조건 |
| ↳ `WS-FEBRUARY` (88) | VALUE 2 | — | 2월 조건(윤년 분기용) |
| `WS-EDIT-DATE-DD` | `PIC X(2)` | 2 | 일 문자 뷰 |
| `WS-EDIT-DATE-DD-N` | `REDEFINES WS-EDIT-DATE-DD` `PIC 9(2)` | 2 | 일 숫자 뷰 |
| ↳ `WS-VALID-DAY` (88) | VALUES 1 THROUGH 31 | — | 일 유효 범위 조건 |
| ↳ `WS-DAY-31` (88) | VALUE 31 | — | 31일 경계 조건 |
| ↳ `WS-DAY-30` (88) | VALUE 30 | — | 30일 경계 조건 |
| ↳ `WS-DAY-29` (88) | VALUE 29 | — | 29일 경계 조건 |
| ↳ `WS-VALID-FEB-DAY` (88) | VALUES 1 THROUGH 28 | — | 2월 평년 유효 일 범위 |
| `WS-EDIT-DATE-CCYYMMDD-N` | `REDEFINES WS-EDIT-DATE-CCYYMMDD` `PIC 9(8)` | 8 | 전체 날짜 숫자 단일 뷰 |

> **REDEFINES 주의**: `WS-EDIT-DATE-CC`와 `WS-EDIT-DATE-CC-N`은 동일한 2바이트 메모리를 공유한다. Java에는 직접 대응 개념이 없으며, `ByteBuffer`의 `getShort`/`getString` 뷰 전환, 또는 단일 `String` 필드와 `Integer.parseInt()`로 이중 접근하는 패턴으로 구현한다.

---

### 그룹 2 — BINARY 변환용 필드

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `WS-EDIT-DATE-BINARY` | `PIC S9(9) BINARY` | 4 (FULLWORD) | 날짜를 정수로 변환한 값(율리우스 일수 또는 경과일 계산용으로 추정) |

> `PIC S9(9) BINARY`는 IBM Enterprise COBOL에서 4바이트 부호 있는 정수(`int`에 해당). Java: `int` 또는 `long`으로 수신. 최대값 999,999,999 → `int` 범위 이내.

---

### 그룹 3 — 현재 날짜 버퍼 (`WS-CURRENT-DATE`, 12바이트)

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `WS-CURRENT-DATE` | 그룹 (12B) | 12 | 현재 날짜+이진 날짜 |
| `WS-CURRENT-DATE-YYYYMMDD` | `PIC X(8)` | 8 | 현재 날짜 문자 뷰 |
| `WS-CURRENT-DATE-YYYYMMDD-N` | `REDEFINES WS-CURRENT-DATE-YYYYMMDD` `PIC 9(8)` | 8 | 현재 날짜 숫자 뷰 |
| `WS-CURRENT-DATE-BINARY` | `PIC S9(9) BINARY` | 4 | 현재 날짜 이진 정수 |

---

### 그룹 4 — 유효성 플래그 (`WS-EDIT-DATE-FLGS`, 3바이트)

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `WS-EDIT-DATE-FLGS` | 그룹 (3B) | 3 | 연·월·일 플래그 묶음 |
| ↳ `WS-EDIT-DATE-IS-VALID` (88) | VALUE LOW-VALUES | — | 3바이트 모두 X'00' → 날짜 전체 유효 |
| ↳ `WS-EDIT-DATE-IS-INVALID` (88) | VALUE '000' | — | ASCII/EBCDIC '0'(X'30') 3개 → 날짜 무효 |
| `WS-EDIT-YEAR-FLG` | `PIC X(1)` | 1 | 연도 플래그 |
| ↳ `FLG-YEAR-ISVALID` (88) | VALUE LOW-VALUES | — | X'00' → 연도 유효 |
| ↳ `FLG-YEAR-NOT-OK` (88) | VALUE '0' | — | '0'(X'30') → 연도 무효 |
| ↳ `FLG-YEAR-BLANK` (88) | VALUE 'B' | — | 'B' → 연도 미입력 |
| `WS-EDIT-MONTH` | `PIC X(1)` | 1 | 월 플래그 |
| ↳ `FLG-MONTH-ISVALID` (88) | VALUE LOW-VALUES | — | X'00' → 월 유효 |
| ↳ `FLG-MONTH-NOT-OK` (88) | VALUE '0' | — | '0' → 월 무효 |
| ↳ `FLG-MONTH-BLANK` (88) | VALUE 'B' | — | 'B' → 월 미입력 |
| `WS-EDIT-DAY` | `PIC X(1)` | 1 | 일 플래그 |
| ↳ `FLG-DAY-ISVALID` (88) | VALUE LOW-VALUES | — | X'00' → 일 유효 |
| ↳ `FLG-DAY-NOT-OK` (88) | VALUE '0' | — | '0' → 일 무효 |
| ↳ `FLG-DAY-BLANK` (88) | VALUE 'B' | — | 'B' → 일 미입력 |

> **LOW-VALUES vs '0' 구별**: EBCDIC에서 LOW-VALUES = X'00'이고 '0' = X'F0'이다. ASCII 환경에서는 '0' = X'30'. "유효"는 X'00'(초기화 상태 유지), "무효"는 문자 '0', "미입력"은 문자 'B'로 삼원 상태를 표현한다. Java로는 `enum { VALID, INVALID, BLANK }` 로 정확히 매핑된다.

---

### 그룹 5 — 포맷 마스크

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `WS-DATE-FORMAT` | `PIC X(8)` VALUE 'YYYYMMDD' | 8 | 날짜 형식 마스크(기본값 고정) |

---

### 그룹 6 — 검증 결과 출력 레코드 (`WS-DATE-VALIDATION-RESULT`, 80바이트)

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| `WS-DATE-VALIDATION-RESULT` | 그룹 (80B) | 80 | 검증 결과 전체 메시지 레코드 |
| `WS-SEVERITY` | `PIC X(4)` | 4 | 심각도 코드 문자 뷰 |
| `WS-SEVERITY-N` | `REDEFINES WS-SEVERITY` `PIC 9(4)` | 4 | 심각도 코드 숫자 뷰 |
| FILLER | `PIC X(11)` VALUE 'Mesg Code:' | 11 | 리터럴 레이블 |
| `WS-MSG-NO` | `PIC X(4)` | 4 | 메시지 번호 문자 뷰 |
| `WS-MSG-NO-N` | `REDEFINES WS-MSG-NO` `PIC 9(4)` | 4 | 메시지 번호 숫자 뷰 |
| FILLER | `PIC X(1)` VALUE SPACE | 1 | 구분자 |
| `WS-RESULT` | `PIC X(15)` | 15 | 결과 문자열('VALID'/'INVALID' 등) |
| FILLER | `PIC X(1)` VALUE SPACE | 1 | 구분자 |
| FILLER | `PIC X(9)` VALUE 'TstDate:' | 9 | 리터럴 레이블 |
| `WS-DATE` | `PIC X(10)` | 10 | 테스트한 날짜 값(표시용) |
| FILLER | `PIC X(1)` VALUE SPACE | 1 | 구분자 |
| FILLER | `PIC X(10)` VALUE 'Mask used:' | 10 | 리터럴 레이블 |
| `WS-DATE-FMT` | `PIC X(10)` | 10 | 적용된 포맷 마스크 |
| FILLER | `PIC X(1)` VALUE SPACE | 1 | 구분자 |
| FILLER | `PIC X(3)` VALUE SPACES | 3 | 후미 패딩 |

> 합계: 4+11+4+4+1+15+1+9+10+1+10+10+1+3 = **84바이트 계산됨**. 그룹 `WS-DATE-VALIDATION-RESULT`의 실제 경계는 컴파일러 레벨 번호(10-레벨)에 의존하므로 상위 01-레벨 레이아웃을 함께 확인해야 한다. **(추측)** 이 copybook은 상위 `01 WS-DATE-EDIT-WORK.`(또는 유사명) 아래 10-레벨로 삽입되어 사용된다.

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — 이 copybook 자체는 다른 copybook을 `COPY`하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음 — copybook 자체는 실행 코드가 아니므로 호출 관계 없음. **(추측)** `CSUTLDTC`(날짜 유틸리티 서브루틴)가 이 레이아웃을 파라미터 영역으로 사용할 것으로 추정되며, 사용 프로그램은 `CALL 'CSUTLDTC' USING WS-EDIT-DATE-CCYYMMDD WS-DATE-VALIDATION-RESULT` 형태로 호출할 가능성이 높다.
- **데이터셋/파일/DB 테이블**: 없음 — 순수 WORKING-STORAGE 레이아웃 정의.
- **트랜잭션 ID 또는 EXEC PGM**: 없음.

---

## Java/현대화 노트

### 1. REDEFINES → 이중 타입 접근 패턴

COBOL의 `REDEFINES`는 동일 메모리에 문자 뷰(`PIC X`)와 숫자 뷰(`PIC 9`)를 동시에 올리는 union 패턴이다. Java에서는 다음 두 방식 중 하나로 구현한다.

```java
// 방법 A: 단일 String 필드 + 파싱 메서드
public class DateEditBuffer {
    private String cc;            // WS-EDIT-DATE-CC (PIC X(2))
    public int getCcAsInt() {     // WS-EDIT-DATE-CC-N (REDEFINES → PIC 9(2))
        return Integer.parseInt(cc);
    }
    public boolean isThisCentury() { return getCcAsInt() == 20; }  // 88-level
    public boolean isLastCentury() { return getCcAsInt() == 19; }
}
```

### 2. 88-레벨 조건명 → enum 또는 boolean 메서드

```java
// WS-31-DAY-MONTH (VALUES 1,3,5,7,8,10,12) 예시
private static final Set<Integer> MONTHS_31 = Set.of(1,3,5,7,8,10,12);
public boolean is31DayMonth(int month) { return MONTHS_31.contains(month); }

// WS-VALID-MONTH (VALUES 1 THROUGH 12)
public boolean isValidMonth(int month) { return month >= 1 && month <= 12; }
```

### 3. 날짜 플래그 삼원 상태 → enum

`LOW-VALUES`/`'0'`/`'B'` 세 값을 `enum`으로 정확히 표현한다.

```java
public enum DateFieldState { VALID, INVALID, BLANK }

public class DateEditFlags {
    private DateFieldState yearFlag;   // WS-EDIT-YEAR-FLG
    private DateFieldState monthFlag;  // WS-EDIT-MONTH
    private DateFieldState dayFlag;    // WS-EDIT-DAY

    public boolean isDateValid() {
        return yearFlag == DateFieldState.VALID
            && monthFlag == DateFieldState.VALID
            && dayFlag == DateFieldState.VALID;
    }
}
```

> COBOL에서 `WS-EDIT-DATE-IS-VALID`는 `LOW-VALUES`(X'00') 3바이트, `WS-EDIT-DATE-IS-INVALID`는 '000'(X'F0F0F0' in EBCDIC). 이 둘이 다른 값임을 반드시 인지해야 한다. Java의 `null` check와 일치하지 않으므로 바이트 변환 시 주의.

### 4. WS-EDIT-DATE-BINARY → int / LocalDate 변환

`PIC S9(9) BINARY`는 4바이트 부호 정수(`int`)다. 이 값이 율리우스 일수(Julian day number)인지 경과일인지는 `CSUTLDTC` 소스 확인 전까지 **(추측)**. Java 마이그레이션 시 `LocalDate.ofEpochDay()`(Unix epoch 기준) 또는 율리우스→`LocalDate` 변환 유틸리티를 준비한다.

```java
// BINARY 날짜가 YYYYMMDD 정수라면
int yyyymmdd = 20231225;
LocalDate date = LocalDate.of(yyyymmdd / 10000,
                              (yyyymmdd / 100) % 100,
                              yyyymmdd % 100);
```

### 5. WS-DATE-VALIDATION-RESULT → 결과 DTO

80바이트 고정 레이아웃 메시지를 Java로 이관할 때는 FILLER 리터럴('Mesg Code:', 'TstDate:' 등)을 제거하고 구조화된 DTO로 전환한다.

```java
public record DateValidationResult(
    int severity,          // WS-SEVERITY-N
    int messageCode,       // WS-MSG-NO-N
    String result,         // WS-RESULT (15자)
    String testDate,       // WS-DATE (10자)
    String formatMask      // WS-DATE-FMT (10자)
) {}
```

### 6. EBCDIC 이관 주의사항

`LOW-VALUES`(X'00')는 EBCDIC/ASCII 공통. 그러나 `'0'`은 EBCDIC=X'F0', ASCII=X'30'이므로 COBOL 바이너리 파일을 Java로 직접 읽을 때 문자 비교 로직이 달라진다. `CSUTLDTC`가 CICS 환경에서 동작한다면 EBCDIC 문자셋 기준으로 검증 로직이 작성된 것임을 감안해야 한다.

---

*버전 정보: CardDemo_v1.0-15-g27d6c6f-68 / 2022-07-19 23:15:59 CDT (라인 88)*
