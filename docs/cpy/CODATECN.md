# CODATECN — 날짜 형식 변환 파라미터 레코드

- **유형**: Copybook (날짜 변환 서브루틴 파라미터 인터페이스)
- **한 줄 요약**: `COBDATFT` 어셈블러 서브루틴에 전달하는 날짜 형식 변환 파라미터 구조체로, 입력 날짜 형식(YYYYMMDD 또는 YYYY-MM-DD)과 출력 형식을 지정하고 변환 결과 및 오류 메시지를 반환받는다.

---

## 기능 설명

`CODATECN`은 날짜 문자열을 한 형식에서 다른 형식으로 변환하는 어셈블러 서브루틴 `COBDATFT`와의 인터페이스 역할을 하는 파라미터 레코드 copybook이다. WORKING-STORAGE에 `COPY CODATECN`으로 포함시킨 뒤, 입력 날짜 값과 형식 코드를 세팅하고 `CALL 'COBDATFT' USING CODATECN-REC`를 호출하면 출력 필드에 변환된 날짜가 채워진다.

지원하는 날짜 형식은 두 가지다:

| 코드 | 형식 | 예시 |
|------|------|------|
| `"1"` | `YYYYMMDD` (구분자 없음) | `20251231` |
| `"2"` | `YYYY-MM-DD` (하이픈 구분) | `2025-12-31` |

입력 타입(`CODATECN-TYPE`)과 출력 타입(`CODATECN-OUTTYPE`)을 독립적으로 지정하므로, 네 가지 방향 변환이 모두 가능하다. 실제 사용 예(CBACT01C, 라인 225–226)에서는 양쪽 모두 `"2"`로 지정해 `YYYY-MM-DD → YYYY-MM-DD` 형식을 유지한 채 재포맷 또는 검증만 수행한다.

변환 중 오류가 발생하면 `CODATECN-ERROR-MSG`(38자)에 메시지가 반환된다. 정상 시 이 필드는 SPACES로 유지된다(추측 — `COBDATFT` 소스 미제공).

---

## 필드 레이아웃

전체 레코드는 `01 CODATECN-REC`(59바이트)이며, 입력 영역(`CODATECN-IN-REC`)과 출력 영역(`CODATECN-OUT-REC`), 그리고 공통 오류 메시지 필드로 구성된다.

### 입력 영역 — `CODATECN-IN-REC` (21바이트)

| 필드명 | PIC/USAGE | 오프셋 | 크기 | 의미 |
|--------|-----------|--------|------|------|
| `CODATECN-TYPE` | `PIC X` | 0 | 1 | 입력 날짜 형식 코드 |
| ↳ `YYYYMMDD-IN` | 88 VALUE `"1"` | — | — | 입력이 `YYYYMMDD` 형식임을 나타내는 조건명 |
| ↳ `YYYY-MM-DD-IN` | 88 VALUE `"2"` | — | — | 입력이 `YYYY-MM-DD` 형식임을 나타내는 조건명 |
| `CODATECN-INP-DATE` | `PIC X(20)` | 1 | 20 | 원본 날짜 문자열(20바이트 고정, 우측 미사용 바이트는 SPACE) |
| `CODATECN-1INP` | **REDEFINES** `CODATECN-INP-DATE` | 1 | 20 | 형식 코드 `"1"` (YYYYMMDD) 파싱 뷰 |
| ↳ `CODATECN-1YYYY` | `PIC XXXX` | 1 | 4 | 연도 4자리 |
| ↳ `CODATECN-1MM` | `PIC XX` | 5 | 2 | 월 2자리 |
| ↳ `CODATECN-1DD` | `PIC XX` | 7 | 2 | 일 2자리 |
| ↳ `CODATECN-1FIL` | `PIC X(12)` | 9 | 12 | 미사용 패딩 (FILLER 역할) |
| `CODATECN-2INP` | **REDEFINES** `CODATECN-INP-DATE` | 1 | 20 | 형식 코드 `"2"` (YYYY-MM-DD) 파싱 뷰 |
| ↳ `CODATECN-1O-YYYY` | `PIC XXXX` | 1 | 4 | 연도 4자리 |
| ↳ `CODATECN-1I-S1` | `PIC X` | 5 | 1 | 첫 번째 구분자 (`-`) |
| ↳ `CODATECN-1MM` | `PIC XX` | 6 | 2 | 월 2자리 |
| ↳ `CODATECN-1I-S2` | `PIC X` | 8 | 1 | 두 번째 구분자 (`-`) |
| ↳ `CODATECN-2YY` | `PIC XX` | 9 | 2 | 일 2자리 (필드명 `2YY`는 오해 소지 있음 — 실제로는 DD) |
| ↳ `CODATECN-2FIL` | `PIC X(10)` | 11 | 10 | 미사용 패딩 |

> **주의**: `CODATECN-2INP` 내의 `CODATECN-1MM`은 `CODATECN-1INP` 내 동일 이름 `CODATECN-1MM`과 **중복 선언**이다(라인 26, 32). IBM Enterprise COBOL은 한정어(qualifier) 없이 이를 허용하지만, 동일 이름 필드 참조 시 `CODATECN-1MM IN CODATECN-2INP` 형태로 한정해야 모호성이 사라진다. Java 마이그레이션 시 별도의 명확한 필드명으로 분리해야 한다.

> **주의**: `CODATECN-2YY`(라인 33)는 필드명에 `YY`가 들어가 있으나 실제로는 **일(DD) 2자리**를 담는 슬롯이다. 서브루틴 `COBDATFT`가 위치 기반으로 파싱하므로 논리적 오류는 없지만, 가독성 버그다.

---

### 출력 영역 — `CODATECN-OUT-REC` (21바이트)

| 필드명 | PIC/USAGE | 오프셋 | 크기 | 의미 |
|--------|-----------|--------|------|------|
| `CODATECN-OUTTYPE` | `PIC X` | 21 | 1 | 출력 날짜 형식 코드 |
| ↳ `YYYY-MM-DD-OP` | 88 VALUE `"1"` | — | — | 출력을 `YYYY-MM-DD`로 내보내는 조건명 |
| ↳ `YYYYMMDD-OP` | 88 VALUE `"2"` | — | — | 출력을 `YYYYMMDD`로 내보내는 조건명 |
| `CODATECN-0UT-DATE` | `PIC X(20)` | 22 | 20 | 변환된 날짜 문자열 (필드명에 숫자 `0` 포함 — 오타 주의) |
| `CODATECN-1OUT` | **REDEFINES** `CODATECN-0UT-DATE` | 22 | 20 | 출력 형식 코드 `"1"` (YYYY-MM-DD) 뷰 |
| ↳ `CODATECN-1O-YYYY` | `PIC XXXX` | 22 | 4 | 연도 4자리 |
| ↳ `CODATECN-1O-S1` | `PIC X` | 26 | 1 | 첫 번째 구분자 |
| ↳ `CODATECN-1O-MM` | `PIC XX` | 27 | 2 | 월 2자리 |
| ↳ `CODATECN-1O-S2` | `PIC X` | 29 | 1 | 두 번째 구분자 |
| ↳ `CODATECN-1O-DD` | `PIC XX` | 30 | 2 | 일 2자리 |
| ↳ `CODATECN-1OFIl` | `PIC X(10)` | 32 | 10 | 패딩 (오탈자: `l`이 소문자) |
| `CODATECN-2OUT` | **REDEFINES** `CODATECN-0UT-DATE` | 22 | 20 | 출력 형식 코드 `"2"` (YYYYMMDD) 뷰 |
| ↳ `CODATECN-2O-YYYY` | `PIC XXXX` | 22 | 4 | 연도 4자리 |
| ↳ `CODATECN-2O-MM` | `PIC XX` | 26 | 2 | 월 2자리 |
| ↳ `CODATECN-2O-DD` | `PIC XX` | 28 | 2 | 일 2자리 |
| ↳ `CODATECN-2OFIl` | `PIC X(12)` | 30 | 12 | 패딩 (오탈자: `l`이 소문자) |

> **주의**: 출력 코드 의미가 입력 코드와 **반전**되어 있다(입력 `"1"` = YYYYMMDD, 출력 `"1"` = YYYY-MM-DD). CBACT01C에서 `CODATECN-TYPE = '2'`, `CODATECN-OUTTYPE = '2'`로 설정(라인 225–226)하는데, 이는 입력 `YYYY-MM-DD`를 받아 출력 `YYYYMMDD`로 변환하는 것이다. 직관과 반대이므로 사용 시 88-level 조건명(`YYYY-MM-DD-IN`, `YYYYMMDD-OP` 등)을 반드시 활용해 가독성을 확보해야 한다.

---

### 공통 오류 필드

| 필드명 | PIC/USAGE | 오프셋 | 크기 | 의미 |
|--------|-----------|--------|------|------|
| `CODATECN-ERROR-MSG` | `PIC X(38)` | 42 | 38 | 변환 실패 시 오류 메시지 (성공 시 SPACES, 추측) |

---

### 전체 레코드 크기 계산

```
CODATECN-TYPE(1) + CODATECN-INP-DATE(20) = 21바이트  → IN-REC
CODATECN-OUTTYPE(1) + CODATECN-0UT-DATE(20) = 21바이트 → OUT-REC
CODATECN-ERROR-MSG(38)
합계: 21 + 21 + 38 = 80바이트 (라인 85의 VBR-REC PIC X(80)과 일치 — 추측)
```

REDEFINES로 정의된 하위 필드(`CODATECN-1INP`, `CODATECN-2INP`, `CODATECN-1OUT`, `CODATECN-2OUT`)는 추가 공간을 차지하지 않는다. 이들은 동일한 20바이트 메모리 영역을 서로 다른 이름으로 해석하는 **메모리 오버레이**다.

---

## 의존성

- **COPY (중첩 카피북)**: 없음 (이 copybook 자체가 다른 copybook을 COPY하지 않음)
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음 (이 copybook은 서브루틴 파라미터 DTO일 뿐이며, 실제 호출은 copybook을 COPY한 프로그램이 수행)
- **데이터셋/파일/DB 테이블**: 없음 (파라미터 레코드이므로 파일 참조 없음)
- **트랜잭션 ID 또는 EXEC PGM**: 없음

### copybook을 사용하는 프로그램

| 프로그램 | COPY 위치 | 사용 목적 |
|---------|-----------|-----------|
| `CBACT01C` | WORKING-STORAGE (라인 90) | `ACCT-REISSUE-DATE`를 `YYYY-MM-DD` → `YYYYMMDD` 변환 후 출력 레코드에 기록. `CALL 'COBDATFT'`(라인 231) |

> `COBDATFT`는 어셈블러로 작성된 외부 날짜 포맷 변환 서브루틴으로 추정된다(라인 228–229 주석: `CALL ASSEMBLER PROGRAM FOR DATE FORMATTING`). 소스가 이 저장소에 포함되어 있지 않아 내부 구현은 확인 불가.

> `CSUTLDTC`는 별도의 날짜 검증 유틸리티 서브루틴(`CORPT00C`, `COTRN02C`에서 사용)으로, `CODATECN`과는 **무관한 독립 인터페이스**이다. 유사한 역할이지만 다른 파라미터 구조를 가진다.

---

## Java/현대화 노트

### 1. REDEFINES — Java에는 직접 대응물이 없다

`CODATECN-1INP`와 `CODATECN-2INP`는 동일한 20바이트(`CODATECN-INP-DATE`)를 두 가지 방식으로 해석하는 메모리 오버레이다. Java에는 이런 구조가 없다. 마이그레이션 시 선택지는 두 가지다:

**옵션 A**: 형식 코드에 따라 분기하는 단일 파서 메서드

```java
// CODATECN-TYPE에 해당하는 열거형
public enum DateFormat {
    YYYYMMDD("1"),       // YYYYMMDD-IN
    YYYY_MM_DD("2");     // YYYY-MM-DD-IN

    private final String cobolCode;
    DateFormat(String code) { this.cobolCode = code; }
}

// CODATECN-REC에 해당하는 DTO
public class DateConversionRequest {
    public DateFormat inputFormat;   // CODATECN-TYPE
    public String     inputDate;     // CODATECN-INP-DATE  PIC X(20)
    public DateFormat outputFormat;  // CODATECN-OUTTYPE
    public String     outputDate;    // CODATECN-0UT-DATE  PIC X(20)
    public String     errorMessage;  // CODATECN-ERROR-MSG PIC X(38)
}
```

**옵션 B**: `COBDATFT`를 Java 서비스로 대체

```java
// COBDATFT 어셈블러 서브루틴을 Java로 대체
public class DateFormatConverter {

    private static final DateTimeFormatter FMT_COMPACT =
        DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FMT_ISO =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * CALL 'COBDATFT' USING CODATECN-REC 에 해당
     *
     * 입력/출력 형식 코드 반전 주의:
     *   inputCode  "1" = YYYYMMDD,   "2" = YYYY-MM-DD
     *   outputCode "1" = YYYY-MM-DD, "2" = YYYYMMDD  ← 반전!
     */
    public String convert(String inputDate,
                          DateFormat inputFormat,
                          DateFormat outputFormat) {
        DateTimeFormatter parser = (inputFormat == DateFormat.YYYYMMDD)
            ? FMT_COMPACT : FMT_ISO;
        DateTimeFormatter formatter = (outputFormat == DateFormat.YYYY_MM_DD)
            ? FMT_ISO : FMT_COMPACT;   // 출력은 반전 논리
        LocalDate date = LocalDate.parse(inputDate.trim(), parser);
        return formatter.format(date);
    }
}
```

### 2. 입/출력 코드 의미 반전 — 버그 유발 요주의

COBOL 코드값과 의미의 반전(`"1"` 입력=YYYYMMDD vs `"1"` 출력=YYYY-MM-DD)은 **설계 결함**이다. Java 구현 시 이를 두 개의 별도 열거형 또는 명확한 상수로 분리하거나, 88-level 조건명을 충실히 번역한 `enum` 상수를 사용해 오류를 예방해야 한다.

### 3. 필드명 오탈자 목록

| COBOL 필드명 | 문제 | 마이그레이션 시 권장 이름 |
|-------------|------|--------------------------|
| `CODATECN-0UT-DATE` | `O`(영문자)가 아닌 `0`(숫자) | `outputDate` |
| `CODATECN-1OFIl`, `CODATECN-2OFIl` | `l`이 소문자 | (제거 또는 `padding` 필드) |
| `CODATECN-2YY` | 실제로는 DD(일) 슬롯인데 YY 명명 | `inputDay` |
| 중복 `CODATECN-1MM` | `CODATECN-1INP`, `CODATECN-2INP` 양쪽에 동일 이름 존재 | 각각 `inputMonth1`, `inputMonth2`로 구분 |

### 4. 고정 길이 문자열 처리

`PIC X(20)` 필드는 EBCDIC 고정 길이 문자열이다. 메인프레임에서는 짧은 날짜 문자열 우측에 **SPACE(0x40)**가 채워진다. Java에서는 `inputDate.trim()`으로 후행 공백을 제거한 뒤 파싱해야 한다. EBCDIC-to-ASCII 변환 시 공백 문자 처리도 주의해야 한다.

### 5. 외부 어셈블러 서브루틴 대체 전략

`COBDATFT`가 단순 형식 변환만 수행한다면 `java.time.LocalDate` + `DateTimeFormatter`로 완전 대체 가능하다. 단, `COBDATFT`가 내부적으로 윤년 검증, 유효 날짜 범위 체크, 또는 에러 코드 반환 로직을 포함할 수 있으므로 마이그레이션 전 어셈블러 소스 또는 테스트 케이스를 통해 동작을 실증 검증해야 한다(추측).

---

*소스 파일*: `/Users/dongryunjeong/Documents/development/aws-mainframe-modernization-carddemo/app/cpy/CODATECN.cpy`
