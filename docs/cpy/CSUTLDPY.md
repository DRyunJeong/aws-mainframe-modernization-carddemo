# CSUTLDPY — 날짜 유효성 검증 PROCEDURE DIVISION Copybook

- **유형**: Copybook (PROCEDURE DIVISION 삽입형)
- **한 줄 요약**: CCYYMMDD 형식 날짜를 연·월·일 단계별로 검증하고, Language Environment의 `CSUTLDTC`(내부적으로 `CEEDAYS`) 서비스로 최종 확인하며, 생년월일 미래 날짜 여부까지 체크하는 재사용 단락 모음.

---

## 기능 설명

CSUTLDPY는 PROCEDURE DIVISION에 직접 삽입되는 단락(paragraph) 집합 copybook이다.
짝을 이루는 WORKING-STORAGE copybook `CSUTLDWY`의 데이터 구조를 공유 메모리로 사용하며,
단독으로는 실행되지 않고 `PERFORM … THRU … EXIT` 패턴으로 호출되어야 한다.

### 제공 단락 목록

| 단락명 | 역할 |
|---|---|
| `EDIT-DATE-CCYYMMDD` | 진입점. 전체 날짜 플래그를 INVALID로 초기화 |
| `EDIT-YEAR-CCYY` | 4자리 연도 검증 (공백·비숫자·세기값 19/20) |
| `EDIT-YEAR-CCYY-EXIT` | `EDIT-YEAR-CCYY` 용 EXIT 단락 |
| `EDIT-MONTH` | 월 검증 (공백·범위 1–12·NUMVAL 변환) |
| `EDIT-MONTH-EXIT` | `EDIT-MONTH` 용 EXIT 단락 |
| `EDIT-DAY` | 일 검증 (공백·범위 1–31·NUMVAL 변환) |
| `EDIT-DAY-EXIT` | `EDIT-DAY` 용 EXIT 단락 |
| `EDIT-DAY-MONTH-YEAR` | 복합 조합 검증 (31일·2월 30일·윤년 2월 29일) |
| `EDIT-DAY-MONTH-YEAR-EXIT` | `EDIT-DAY-MONTH-YEAR` 용 EXIT 단락 |
| `EDIT-DATE-LE` | LE(Language Environment) `CSUTLDTC` 서비스 호출 최종 확인 |
| `EDIT-DATE-LE-EXIT` | `EDIT-DATE-LE` 용 EXIT 단락 |
| `EDIT-DATE-CCYYMMDD-EXIT` | 전체 플로우 최종 EXIT 단락. 여기 도달 시 VALID 세트 |
| `EDIT-DATE-OF-BIRTH` | `FUNCTION CURRENT-DATE` 대비 미래 날짜 여부 체크 |
| `EDIT-DATE-OF-BIRTH-EXIT` | `EDIT-DATE-OF-BIRTH` 용 EXIT 단락 |

### 전체 검증 흐름

```
PERFORM EDIT-DATE-CCYYMMDD THRU EDIT-DATE-CCYYMMDD-EXIT
    ├─ EDIT-DATE-CCYYMMDD    : WS-EDIT-DATE-IS-INVALID 세트 (초기화)
    ├─ EDIT-YEAR-CCYY        : CC=19|20, YY 숫자 확인
    ├─ EDIT-MONTH            : MM 범위 1–12, NUMVAL 변환
    ├─ EDIT-DAY              : DD 범위 1–31, NUMVAL 변환
    ├─ EDIT-DAY-MONTH-YEAR   : 월별 최대일, 윤년 계산
    ├─ EDIT-DATE-LE          : CALL 'CSUTLDTC' → LE CEEDAYS 최종 확인
    └─ EDIT-DATE-CCYYMMDD-EXIT : WS-EDIT-DATE-IS-VALID 세트

PERFORM EDIT-DATE-OF-BIRTH THRU EDIT-DATE-OF-BIRTH-EXIT (별도 선택 호출)
    └─ 현재 날짜(FUNCTION CURRENT-DATE) > 입력 날짜 이어야 유효
```

### 윤년 판정 로직 (라인 243–272)

```cobol
IF WS-EDIT-DATE-YY-N = 0          ← 연도 끝 두 자리가 00 (세기 경계)
   MOVE 400 TO WS-DIV-BY          ← 400으로 나눔 (예: 2000년)
ELSE
   MOVE 4   TO WS-DIV-BY          ← 4로 나눔 (일반 윤년)
END-IF
DIVIDE WS-EDIT-DATE-CCYY-N BY WS-DIV-BY
    GIVING WS-DIVIDEND REMAINDER WS-REMAINDER
IF WS-REMAINDER = ZEROES → 윤년, ELSE → 오류
```

> 주의: 이 로직은 세기 경계(YY=00)를 400 나눗셈으로 처리하지만, **100으로 나누어 떨어지면서 400으로는 안 되는 해(예: 1900)**를 별도로 배제하지 않는다. 즉 `CC=20, YY=00`(2000년)은 400 분기로 정확하지만, `CC=19, YY=00`(1900년)을 입력할 경우 **400으로 나뉘지 않아** 오류가 올바르게 반환된다. 완전한 프로레프티 윤년 알고리즘(÷4 AND NOT ÷100 OR ÷400)과는 구현이 다르므로 Java 변환 시 `java.time.Year.isLeap()`로 대체해야 한다.

### `EDIT-DATE-LE` — CSUTLDTC 서비스 호출 (라인 293–321)

```cobol
CALL 'CSUTLDTC'
    USING WS-EDIT-DATE-CCYYMMDD      ← 검증 대상 날짜 8바이트
        , WS-DATE-FORMAT             ← 포맷 문자열 'YYYYMMDD'
        , WS-DATE-VALIDATION-RESULT  ← 결과 구조체 (아래 표 참조)
```

`CSUTLDTC`는 IBM Language Environment의 `CEEDAYS` API 래퍼다.
`CEEDAYS`는 날짜 문자열을 Lillian 날짜(기원 1582-10-15 기준 일련번호)로 변환하면서
달력 규칙을 검증한다. 변환 실패 시 `WS-SEVERITY-N > 0`을 반환한다.

---

## 필드 레이아웃

이 copybook 자체에는 데이터 선언이 없다. 모든 데이터는 짝 copybook **CSUTLDWY**와
호출 프로그램 WORKING-STORAGE에서 공급된다. 아래는 참조 필드 전체 목록이다.

### 1. 입력 날짜 구조 (CSUTLDWY 제공, 라인 4–36)

| 필드명 | PIC / USAGE | 설명 |
|---|---|---|
| `WS-EDIT-DATE-CCYYMMDD` | 01 그룹 (8바이트) | 날짜 전체 문자열 CCYYMMDD |
| `WS-EDIT-DATE-CC` | `PIC X(2)` | 세기 문자 (예: `'20'`) |
| `WS-EDIT-DATE-CC-N` | `REDEFINES WS-EDIT-DATE-CC PIC 9(2)` | 세기 숫자 뷰 |
| `THIS-CENTURY` (88) | VALUE 20 | `WS-EDIT-DATE-CC-N = 20` 조건명 |
| `LAST-CENTURY` (88) | VALUE 19 | `WS-EDIT-DATE-CC-N = 19` 조건명 |
| `WS-EDIT-DATE-YY` | `PIC X(2)` | 연도 하위 2자리 문자 |
| `WS-EDIT-DATE-YY-N` | `REDEFINES WS-EDIT-DATE-YY PIC 9(2)` | 연도 하위 숫자 뷰 |
| `WS-EDIT-DATE-CCYY` | 20 그룹 (4바이트) | CC + YY 합산 4자리 연도 |
| `WS-EDIT-DATE-CCYY-N` | `REDEFINES WS-EDIT-DATE-CCYY PIC 9(4)` | 4자리 연도 숫자 뷰 |
| `WS-EDIT-DATE-MM` | `PIC X(2)` | 월 문자 |
| `WS-EDIT-DATE-MM-N` | `REDEFINES WS-EDIT-DATE-MM PIC 9(2)` | 월 숫자 뷰 |
| `WS-VALID-MONTH` (88) | VALUES 1 THROUGH 12 | 유효 월 조건명 |
| `WS-31-DAY-MONTH` (88) | VALUES 1,3,5,7,8,10,12 | 31일 허용 월 조건명 |
| `WS-FEBRUARY` (88) | VALUE 2 | 2월 조건명 |
| `WS-EDIT-DATE-DD` | `PIC X(2)` | 일 문자 |
| `WS-EDIT-DATE-DD-N` | `REDEFINES WS-EDIT-DATE-DD PIC 9(2)` | 일 숫자 뷰 |
| `WS-VALID-DAY` (88) | VALUES 1 THROUGH 31 | 유효 일 조건명 |
| `WS-DAY-31` (88) | VALUE 31 | 31일 조건명 |
| `WS-DAY-30` (88) | VALUE 30 | 30일 조건명 |
| `WS-DAY-29` (88) | VALUE 29 | 29일 조건명 |
| `WS-EDIT-DATE-CCYYMMDD-N` | `REDEFINES WS-EDIT-DATE-CCYYMMDD PIC 9(8)` | 날짜 전체 숫자 뷰 |

> **REDEFINES 설명**: COBOL의 `REDEFINES`는 동일한 메모리 영역을 다른 타입으로 오버레이하는 C의 `union`과 유사하다. 예를 들어 `WS-EDIT-DATE-MM`(PIC X(2))과 `WS-EDIT-DATE-MM-N`(PIC 9(2))은 완전히 같은 2바이트를 공유한다. Java에는 직접 대응 개념이 없으며, 변환 시 `String` 파싱(`Integer.parseInt(mm)`)으로 처리한다.

### 2. 이진 날짜 및 현재 날짜 (CSUTLDWY 제공, 라인 37–42)

| 필드명 | PIC / USAGE | 설명 |
|---|---|---|
| `WS-EDIT-DATE-BINARY` | `PIC S9(9) BINARY` | 입력 날짜 INTEGER-OF-DATE 결과 (부호 있는 4바이트 정수) |
| `WS-CURRENT-DATE-YYYYMMDD` | `PIC X(8)` | FUNCTION CURRENT-DATE에서 추출한 오늘 날짜 |
| `WS-CURRENT-DATE-YYYYMMDD-N` | `REDEFINES … PIC 9(8)` | 오늘 날짜 숫자 뷰 |
| `WS-CURRENT-DATE-BINARY` | `PIC S9(9) BINARY` | 오늘 날짜 INTEGER-OF-DATE 결과 |

> `PIC S9(9) BINARY`는 IBM 메인프레임에서 4바이트 부호 있는 2진 정수(`int`와 동일). Java 매핑: `int` 또는 `long`.

### 3. 검증 플래그 (CSUTLDWY 제공, 라인 43–57)

| 필드명 | PIC / 88-level | 의미 |
|---|---|---|
| `WS-EDIT-DATE-FLGS` | 01 그룹 3바이트 | 연·월·일 플래그 컨테이너 |
| `WS-EDIT-DATE-IS-VALID` (88) | VALUE LOW-VALUES | 3바이트 모두 LOW-VALUES → 전체 유효 |
| `WS-EDIT-DATE-IS-INVALID` (88) | VALUE '000' | 초기 무효 상태 |
| `WS-EDIT-YEAR-FLG` | `PIC X(01)` | 연도 플래그 |
| `FLG-YEAR-ISVALID` (88) | VALUE LOW-VALUES | 연도 유효 |
| `FLG-YEAR-NOT-OK` (88) | VALUE '0' | 연도 오류 |
| `FLG-YEAR-BLANK` (88) | VALUE 'B' | 연도 미입력 |
| `WS-EDIT-MONTH` | `PIC X(01)` | 월 플래그 |
| `FLG-MONTH-ISVALID` (88) | VALUE LOW-VALUES | 월 유효 |
| `FLG-MONTH-NOT-OK` (88) | VALUE '0' | 월 오류 |
| `FLG-MONTH-BLANK` (88) | VALUE 'B' | 월 미입력 |
| `WS-EDIT-DAY` | `PIC X(01)` | 일 플래그 |
| `FLG-DAY-ISVALID` (88) | VALUE LOW-VALUES | 일 유효 |
| `FLG-DAY-NOT-OK` (88) | VALUE '0' | 일 오류 |
| `FLG-DAY-BLANK` (88) | VALUE 'B' | 일 미입력 |

> **88-level 조건명은 Java의 boolean 필드 또는 enum 상수에 해당한다.** 예를 들어 `FLG-YEAR-ISVALID`는 `isYearValid == true`로, `FLG-YEAR-BLANK`는 별도 `blankYear == true`로 매핑한다.

### 4. CSUTLDTC 결과 구조 (CSUTLDWY 제공, 라인 58–85)

| 필드명 | PIC / USAGE | 의미 |
|---|---|---|
| `WS-DATE-FORMAT` | `PIC X(08)` VALUE `'YYYYMMDD'` | CEEDAYS 포맷 마스크 |
| `WS-DATE-VALIDATION-RESULT` | 01 그룹 (65바이트) | CSUTLDTC 반환 결과 전체 |
| `WS-SEVERITY` | `PIC X(04)` | 심각도 코드 문자 |
| `WS-SEVERITY-N` | `REDEFINES … PIC 9(4)` | 심각도 코드 숫자 (0 = 성공) |
| `WS-MSG-NO` | `PIC X(04)` | LE 메시지 번호 문자 |
| `WS-MSG-NO-N` | `REDEFINES … PIC 9(4)` | 메시지 번호 숫자 |
| `WS-RESULT` | `PIC X(15)` | 결과 텍스트 |
| `WS-DATE` | `PIC X(10)` | 검증에 사용된 날짜 표시 |
| `WS-DATE-FMT` | `PIC X(10)` | 사용된 포맷 마스크 표시 |

### 5. 호출 프로그램 WORKING-STORAGE (COACTUPC 등에서 확인, 라인 53·152–157·173·479)

| 필드명 | PIC / USAGE | 의미 |
|---|---|---|
| `WS-EDIT-VARIABLE-NAME` | `PIC X(25)` | 검증 중인 필드명 (오류 메시지 구성용) |
| `WS-RETURN-MSG` | `PIC X(75)` | 오류 메시지 반환 버퍼 |
| `WS-RETURN-MSG-OFF` (88) | VALUE SPACES | 메시지 버퍼 비어있음 (추가 기록 허용) |
| `INPUT-ERROR` (88) | VALUE '1' | 전체 입력 오류 플래그 |
| `WS-DIV-BY` | `PIC S9(4) COMP-3` | 윤년 나눗셈 제수 (4 또는 400) |
| `WS-DIVIDEND` | `PIC S9(4) COMP-3` | 나눗셈 몫 |
| `WS-REMAINDER` | `PIC S9(4) COMP-3` | 나눗셈 나머지 |

> `PIC S9(4) COMP-3`은 packed decimal(BCD). 4자리 + 부호 → 3바이트 저장. Java 매핑: `int`(단순 계산용이므로 `BigDecimal` 불필요).

---

## 의존성

- **COPY (중첩 카피북)**:
  - `CSUTLDWY` — 짝 WORKING-STORAGE copybook. 날짜 구조(`WS-EDIT-DATE-CCYYMMDD`), 검증 플래그(`WS-EDIT-DATE-FLGS`), CSUTLDTC 결과(`WS-DATE-VALIDATION-RESULT`) 등 이 copybook에서 참조하는 모든 데이터 선언 포함. **반드시 함께 COPY해야 한다.**

- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `CSUTLDTC` — IBM Language Environment `CEEDAYS` API 래퍼 프로그램 (라인 293). 날짜 문자열을 Lillian 일련번호로 변환하며, 변환 실패 시 `WS-SEVERITY-N > 0` 반환. **런타임 CALL이므로 링크 시 LE 라이브러리 필요.**

- **데이터셋 / 파일 / DB 테이블**: 없음 (모든 처리가 메모리 내 단락 로직)

- **트랜잭션 ID 또는 EXEC PGM**: 없음

---

## Java / 현대화 노트

### 1. PROCEDURE DIVISION copybook → 정적 유틸리티 메서드

COBOL PROCEDURE DIVISION copybook은 Java에 직접 대응하는 개념이 없다.
가장 자연스러운 매핑은 **정적 유틸리티 클래스**이다.

```java
public final class DateValidator {

    /**
     * EDIT-DATE-CCYYMMDD 전체 플로우에 해당.
     * @param dateStr CCYYMMDD 형식 8자리 문자열
     * @param fieldName WS-EDIT-VARIABLE-NAME 에 해당 (오류 메시지용)
     * @return ValidationResult (플래그 + 오류 메시지)
     */
    public static ValidationResult validateCcyymmdd(String dateStr, String fieldName) {
        ValidationResult result = new ValidationResult();
        if (!validateYear(dateStr, fieldName, result))   return result;
        if (!validateMonth(dateStr, fieldName, result))  return result;
        if (!validateDay(dateStr, fieldName, result))    return result;
        if (!validateCombination(dateStr, fieldName, result)) return result;
        // EDIT-DATE-LE 에 해당 — java.time 으로 최종 파싱 확인
        try {
            LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE);
            result.setValid(true);
        } catch (DateTimeParseException e) {
            result.addError(fieldName + " validation error: " + e.getMessage());
        }
        return result;
    }

    /** EDIT-DATE-OF-BIRTH 에 해당 */
    public static boolean isNotFutureDate(String dateStr) {
        LocalDate input = LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE);
        return LocalDate.now().isAfter(input);
    }
}
```

### 2. CSUTLDTC(CEEDAYS) → java.time

`CSUTLDTC`가 수행하는 역할(날짜 문자열 → 달력 유효성 검증)은
Java에서 `LocalDate.parse()` 한 줄로 대체된다.
Lillian 날짜 일련번호 자체가 필요한 경우 `ChronoUnit.DAYS.between(EPOCH, date)`로 구현한다.

```java
// EDIT-DATE-LE 의 CALL 'CSUTLDTC' 대체
try {
    LocalDate.parse(ws_edit_date_ccyymmdd, DateTimeFormatter.BASIC_ISO_DATE);
    wsSeverityN = 0;  // 성공
} catch (DateTimeParseException e) {
    wsSeverityN = 1;  // 오류
    wsReturnMsg = fieldName + " validation error: " + e.getMessage();
}
```

### 3. REDEFINES → String 파싱

`WS-EDIT-DATE-CC`(X(2))와 `WS-EDIT-DATE-CC-N`(9(2))처럼 동일 메모리를 공유하는 패턴은
Java에서는 필드를 하나(`String cc`)로 유지하고 필요 시 파싱한다.

```java
String cc = dateStr.substring(0, 2);
int ccN = Integer.parseInt(cc);          // REDEFINES 숫자 뷰 대응
boolean thisCentury = ccN == 20;         // 88-level THIS-CENTURY 대응
boolean lastCentury = ccN == 19;         // 88-level LAST-CENTURY 대응
```

### 4. GO TO … EXIT 패턴 → 조기 리턴

이 copybook은 COBOL 구조적 프로그래밍 위반을 **의도적으로** 주석에 명시하며(라인 41)
`GO TO EDIT-YEAR-CCYY-EXIT` 같은 단락 내 조기 탈출을 사용한다.
Java로 변환 시 **`return`으로 대체**하고 `EXIT` 단락은 제거한다.

### 5. 88-level 다중 플래그 → ValidationResult DTO

COBOL의 3개 플래그(연·월·일)와 `INPUT-ERROR` 전역 플래그를 Java DTO로 구조화한다.

```java
public class ValidationResult {
    private boolean valid;
    private boolean yearValid, monthValid, dayValid;
    private boolean yearBlank, monthBlank, dayBlank;
    private String errorMessage = "";

    public boolean isInputError() {
        return !yearValid || !monthValid || !dayValid;
    }
}
```

### 6. 윤년 로직 → Year.isLeap()

COBOL 구현의 윤년 판정은 완전하지 않으므로(100 배수 배제 규칙 미구현) 반드시 표준 API를 사용한다.

```java
// EDIT-DAY-MONTH-YEAR 의 윤년 검사 대체
if (month == 2 && day == 29) {
    if (!Year.isLeap(year)) {
        result.addError(fieldName + ": Not a leap year.");
    }
}
```

### 7. PERFORM … THRU … EXIT 호출 방식 유의사항

COBOL의 `PERFORM EDIT-DATE-CCYYMMDD THRU EDIT-DATE-CCYYMMDD-EXIT`는
단락들이 소스 순서대로 **fall-through** 실행된다는 것을 의미한다.
`EDIT-DATE-CCYYMMDD` 단락 자체는 플래그 초기화만 하고, 실제 검증은
`EDIT-YEAR-CCYY`, `EDIT-MONTH`, `EDIT-DAY`, `EDIT-DAY-MONTH-YEAR`, `EDIT-DATE-LE`
단락들이 순서대로 fall-through되면서 수행된다 — 각 단락이 `PERFORM`으로 명시 호출되는 것이 **아님에 주의**.
Java로 변환 시 이 암묵적 순서 의존성을 메서드 호출 체인으로 명시해야 한다.

### 8. 사용 프로그램 목록

- `COACTUPC.cbl` — 계정 업데이트 온라인 프로그램 (Open Date, Expiry Date, Date of Birth 등 다수 날짜 필드 검증에 사용)
- `CORPT00C.cbl` — 리포트 일자 범위 입력 검증
- `COTRN02C.cbl` — 거래 날짜 입력 검증

---

*버전: CardDemo_v1.0-15-g27d6c6f-68 / 2022-07-19*
