# CSUTLDTC — 날짜 유효성 검증 유틸리티 (CEEDAYS 래퍼)

- **유형**: 배치 COBOL (서브루틴 / CALL 대상 유틸리티)
- **한 줄 요약**: 호출자가 넘긴 날짜 문자열과 포맷 마스크를 IBM Language Environment의 `CEEDAYS` API에 전달해 날짜의 유효성을 검증하고, 결과 심각도 코드와 진단 메시지를 반환하는 공유 유틸리티 서브루틴이다.

---

## 기능 설명

CSUTLDTC는 CardDemo 애플리케이션 전체에서 날짜 입력 검증을 처리하는 단일 책임 서브루틴이다. 이 프로그램은 독립 실행이 불가하며, 반드시 `CALL 'CSUTLDTC'` 형태로 다른 프로그램에서 호출된다.

핵심 동작은 다음 두 단계로 요약된다.

1. 호출자가 넘긴 날짜 문자열(`LS-DATE`)과 포맷 마스크(`LS-DATE-FORMAT`)를 IBM LE(Language Environment) 런타임 API인 `CEEDAYS`에 전달한다.
2. `CEEDAYS`가 반환한 피드백 코드(8바이트 구조체)를 해석해 사람이 읽을 수 있는 15자 결과 메시지와 심각도 숫자를 80바이트 결과 버퍼(`LS-RESULT`)에 담아 호출자에게 돌려준다.

`CEEDAYS`는 날짜가 유효하면 Lillian 날짜(율리우스력 기준 일련번호, 1582년 10월 15일 = 1)를 `OUTPUT-LILLIAN`에 채워 주지만, CSUTLDTC는 이 값을 호출자에게 노출하지 않는다. 목적은 날짜 변환이 아닌 **유효성 판별**에만 집중한다.

---

## 입력 / 출력

- **입력**:
  - `LS-DATE` (`PIC X(10)`, LINKAGE SECTION 라인 84): 검증할 날짜 문자열. 예: `2022-07-19` 또는 `20220719`
  - `LS-DATE-FORMAT` (`PIC X(10)`, 라인 85): `CEEDAYS`에 전달할 포맷 마스크. CORPT00C·COTRN02C에서 실제로 사용되는 값은 `YYYY-MM-DD` (라인 10자리).

- **출력**:
  - `LS-RESULT` (`PIC X(80)`, 라인 86): 80바이트 진단 버퍼. 내부 구조는 아래와 같다.

    | 오프셋 | 길이 | 필드명 | 내용 |
    |--------|------|--------|------|
    | 1–4   | 4    | `WS-SEVERITY` | 피드백 심각도 숫자 문자열 (`'0000'` = 정상) |
    | 5–15  | 11   | `FILLER`      | 리터럴 `'Mesg Code:'` |
    | 16–19 | 4    | `WS-MSG-NO`   | CEEDAYS 메시지 번호 문자열 |
    | 20    | 1    | `FILLER`      | 공백 |
    | 21–35 | 15   | `WS-RESULT`   | 사람이 읽을 수 있는 결과 설명 (아래 표 참조) |
    | 36    | 1    | `FILLER`      | 공백 |
    | 37–45 | 9    | `FILLER`      | 리터럴 `'TstDate:'` |
    | 46–55 | 10   | `WS-DATE`     | 검증된 날짜 원문 |
    | 56    | 1    | `FILLER`      | 공백 |
    | 57–66 | 10   | `FILLER`      | 리터럴 `'Mask used:'` |
    | 67–76 | 10   | `WS-DATE-FMT` | 사용된 포맷 마스크 |

  - `RETURN-CODE` (라인 98): 프로그램 리턴 코드에 `WS-SEVERITY-N`(심각도 숫자)을 설정한다. 정상이면 `0`.

---

## 의존성

- **COPY (카피북)**: 없음. `COPY` 문이 존재하지 않는다. CODATECN.cpy는 이 프로그램과 독립적으로 날짜 변환용으로 설계된 copybook이며, CSUTLDTC가 직접 참조하지는 않는다.

- **호출 프로그램 (CALL/XCTL/LINK)**:
  - **피호출(callee)**: `CEEDAYS` — IBM Language Environment 런타임 API. 외부 링크 대상으로, 메인프레임 LE 라이브러리에 위치한다. COBOL 소스에 정의되지 않은 시스템 루틴이다 (라인 116).
  - **호출자(callers)**:
    - `CORPT00C` — CICS 온라인: 리포트 기간의 시작일·종료일 검증에 두 번 호출
    - `COTRN02C` — CICS 온라인: 거래 원본 일자(`TORIGDTI`)·처리 일자(`TPROCDTI`) 검증에 두 번 호출

- **데이터셋/파일/DB 테이블**: 없음. 파일 I/O 및 DB 접근 없음.

- **트랜잭션 ID 또는 EXEC PGM**: 없음. CICS 트랜잭션으로 직접 진입하지 않으며, JCL EXEC PGM으로도 단독 실행되지 않는다.

---

## 핵심 로직 흐름

```
PROCEDURE DIVISION USING LS-DATE, LS-DATE-FORMAT, LS-RESULT
│
├─ INITIALIZE WS-MESSAGE               ← 결과 버퍼 초기화
├─ PERFORM A000-MAIN THRU A000-MAIN-EXIT
│   │
│   ├─ MOVE LENGTH OF LS-DATE  → Vstring-length OF WS-DATE-TO-TEST   (라인 105-106)
│   │      ← LE 가변 문자열 구조체(Vstring) 헤더에 실제 길이 설정
│   ├─ MOVE LS-DATE            → Vstring-text  OF WS-DATE-TO-TEST    (라인 107)
│   ├─ MOVE LS-DATE            → WS-DATE       (진단 버퍼 보존용)    (라인 108)
│   │
│   ├─ MOVE LENGTH OF LS-DATE-FORMAT → Vstring-length OF WS-DATE-FORMAT  (라인 109-110)
│   ├─ MOVE LS-DATE-FORMAT     → Vstring-text  OF WS-DATE-FORMAT     (라인 111-112)
│   ├─ MOVE LS-DATE-FORMAT     → WS-DATE-FMT   (진단 버퍼 보존용)   (라인 113)
│   ├─ MOVE 0                  → OUTPUT-LILLIAN (초기화)             (라인 114)
│   │
│   ├─ CALL "CEEDAYS" USING                                          (라인 116-120)
│   │       WS-DATE-TO-TEST     ← 입력: LE Vstring 형식의 날짜
│   │       WS-DATE-FORMAT      ← 입력: LE Vstring 형식의 포맷 마스크
│   │       OUTPUT-LILLIAN      ← 출력: Lillian 일련번호 (사용 안 함)
│   │       FEEDBACK-CODE       ← 출력: 8바이트 피드백 토큰
│   │
│   ├─ MOVE SEVERITY OF FEEDBACK-CODE → WS-SEVERITY-N               (라인 123)
│   ├─ MOVE MSG-NO   OF FEEDBACK-CODE → WS-MSG-NO-N                 (라인 124)
│   │
│   └─ EVALUATE TRUE  (피드백 토큰 → 메시지 매핑)                   (라인 128-149)
│         WHEN FC-INVALID-DATE      → 'Date is valid'
│         │    ※ 주의: 88레벨 이름이 오해를 유발함. 실제로는 "유효한 날짜" 상태
│         │      FEEDBACK-TOKEN-VALUE = X'0000000000000000'이 정상 반환값
│         WHEN FC-INSUFFICIENT-DATA → 'Insufficient'
│         WHEN FC-BAD-DATE-VALUE    → 'Datevalue error'
│         WHEN FC-INVALID-ERA       → 'Invalid Era    '
│         WHEN FC-UNSUPP-RANGE      → 'Unsupp. Range  '
│         WHEN FC-INVALID-MONTH     → 'Invalid month  '
│         WHEN FC-BAD-PIC-STRING    → 'Bad Pic String '
│         WHEN FC-NON-NUMERIC-DATA  → 'Nonnumeric data'
│         WHEN FC-YEAR-IN-ERA-ZERO  → 'YearInEra is 0 '
│         WHEN OTHER                → 'Date is invalid'
│
├─ MOVE WS-MESSAGE → LS-RESULT                                       (라인 97)
├─ MOVE WS-SEVERITY-N → RETURN-CODE                                  (라인 98)
└─ EXIT PROGRAM                                                      (라인 100)
```

### 중요한 88레벨 이름 혼동 주의

라인 62의 `FC-INVALID-DATE`는 그 이름과 달리 **날짜가 유효함**을 나타내는 정상(NORMAL) 상태다. `FEEDBACK-TOKEN-VALUE = X'0000000000000000'`은 오류 없음을 의미한다. 호출자(CORPT00C, COTRN02C)는 이 점을 감안해 `WS-RESULT`의 문자열 `'Date is valid'`를 체크하거나, `WS-SEVERITY-N = 0`을 체크해 정상 여부를 판단한다.

### CEEDAYS Vstring 인터페이스 구조

`WS-DATE-TO-TEST`와 `WS-DATE-FORMAT`은 IBM LE의 `Vstring` 규격을 따르는 가변 길이 문자열 구조체다:

```
01 WS-DATE-TO-TEST.
    02 Vstring-length  PIC S9(4) BINARY.     ← 2바이트 길이 헤더 (big-endian signed)
    02 Vstring-text.
        03 Vstring-char PIC X
                        OCCURS 0 TO 256 TIMES
                        DEPENDING ON Vstring-length  ← ODO(Occurs Depending On) 구조
```

`LENGTH OF LS-DATE`는 컴파일 타임 상수 10을 생성하므로, 실행 시 항상 10이 헤더에 들어간다.

---

## Java/현대화 노트

### 1. 전체 역할 매핑

| COBOL 구성 요소 | Java 동등 개념 |
|----------------|---------------|
| `CALL 'CSUTLDTC'` 서브루틴 전체 | `DateValidator.validate(String date, String format)` static 메서드 |
| `LINKAGE SECTION` 3개 파라미터 | 메서드 인자 2개 + 반환 객체 1개 |
| `CEEDAYS` API 호출 | `java.time.LocalDate.parse(date, DateTimeFormatter.ofPattern(format))` |
| `FEEDBACK-CODE` 구조체 | `ValidationResult` 값 객체 (severity, messageCode, description) |
| `RETURN-CODE` 설정 | 메서드 반환값 또는 예외 |

### 2. Java 마이그레이션 구현 예시

```java
public class DateValidator {

    public record ValidationResult(
        int severityCode,    // WS-SEVERITY-N에 해당; 0 = 정상
        String messageCode,  // WS-MSG-NO에 해당
        String description,  // WS-RESULT 15자 메시지에 해당
        String testedDate,   // WS-DATE (원본 날짜 문자열)
        String maskUsed      // WS-DATE-FMT (사용된 포맷)
    ) {}

    /**
     * CSUTLDTC의 Java 대응 메서드.
     * COBOL: CALL 'CSUTLDTC' USING LS-DATE, LS-DATE-FORMAT, LS-RESULT
     */
    public static ValidationResult validate(String date, String format) {
        // CEEDAYS → java.time.LocalDate.parse
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                toJavaPattern(format)); // CEEDAYS 마스크 → Java 패턴 변환 필요
            LocalDate.parse(date.trim(), formatter);
            // FC-INVALID-DATE (역설적으로 "정상") 케이스
            return new ValidationResult(0, "0000", "Date is valid", date, format);
        } catch (DateTimeParseException e) {
            // WHEN OTHER → 'Date is invalid'
            return new ValidationResult(309, "???", "Date is invalid", date, format);
        }
    }

    /**
     * CEEDAYS 포맷 마스크를 Java DateTimeFormatter 패턴으로 변환.
     * 예: "YYYY-MM-DD" → "yyyy-MM-dd"
     * 주의: CEEDAYS는 대문자 YYYY를 사용하지만 Java는 소문자 yyyy 사용.
     */
    private static String toJavaPattern(String ceeDaysMask) {
        return ceeDaysMask
            .replace("YYYY", "yyyy")
            .replace("MM", "MM")
            .replace("DD", "dd");
    }
}
```

### 3. 마이그레이션 시 주의 사항

**`FC-INVALID-DATE` 이름 혼동 (가장 중요한 함정)**
라인 62의 88레벨 조건명 `FC-INVALID-DATE`는 이름과 반대로 **정상 상태**를 나타낸다. `EVALUATE` 블록에서 이 조건이 참일 때 `'Date is valid'`를 반환하는 것이 그 증거다. Java로 재구현할 때 이 이름을 그대로 따라가면 로직이 역전된다. `VALID_DATE` 또는 `NO_ERROR`로 명칭을 바꾸어야 한다.

**CEEDAYS 포맷 마스크와 Java 패턴의 차이**
CEEDAYS는 IBM LE 고유의 날짜 포맷 피처 코드(picture string)를 사용한다. 예를 들어 `CEEDAYS`에서 `YYYY`는 4자리 연도이지만, Java의 `DateTimeFormatter`에서 `YYYY`는 ISO 주(week-based) 연도를 의미한다. 반드시 소문자 `yyyy`로 변환해야 한다.

**Lillian 날짜 미노출**
`OUTPUT-LILLIAN`(PIC S9(9) BINARY, 32비트 부호 있는 정수)은 LE 내부 일련번호로, Java의 `LocalDate.toEpochDay()`와 유사하지만 기준일이 다르다(LE: 1582-10-15 = 1 / Java: 1970-01-01 = 0). CSUTLDTC는 이 값을 호출자에게 반환하지 않으므로 마이그레이션 시 계산 로직 추가가 필요하다면 `LocalDate.toEpochDay() + 141427L`로 변환할 수 있다 (추측: 기준일 차이 보정값이며, 실제 LE 문서로 검증 필요).

**REDEFINES 구조체 (라인 44, 74–77)**
`WS-SEVERITY-N REDEFINES WS-SEVERITY`와 `CASE-2-CONDITION-ID REDEFINES CASE-1-CONDITION-ID`는 같은 메모리를 문자열과 숫자로 동시에 해석한다. Java에는 직접 대응 개념이 없으며, `ByteBuffer`를 사용하거나 별도의 변환 로직이 필요하다.

**`EXIT PROGRAM` vs `GOBACK`**
라인 100의 `EXIT PROGRAM`은 호출자에게 제어를 돌려주는 정상 서브루틴 종료다. `GOBACK`(라인 101에 주석 처리됨)은 같은 목적으로 사용될 수 있지만, 메인 프로그램에서 호출 시 JVM 종료에 해당하는 동작을 유발한다. Java의 `return`에 해당한다.

**ODO(Occurs Depending On) 배열 (라인 29–31)**
`Vstring-char PIC X OCCURS 0 TO 256 TIMES DEPENDING ON Vstring-length`는 길이 헤더가 결정하는 가변 배열이다. Java에서는 단순히 `String.substring(0, length)` 또는 `byte[]` 슬라이싱으로 처리한다.

### 4. 호출 패턴 (CORPT00C·COTRN02C 공통)

```cobol
MOVE someDate           TO CSUTLDTC-DATE
MOVE WS-DATE-FORMAT     TO CSUTLDTC-DATE-FORMAT
MOVE SPACES             TO CSUTLDTC-RESULT
CALL 'CSUTLDTC' USING   CSUTLDTC-DATE
                        CSUTLDTC-DATE-FORMAT
                        CSUTLDTC-RESULT
IF CSUTLDTC-RESULT-SEV-CD = '0000'
    IF CSUTLDTC-RESULT-MSG-NUM NOT = '2513'  ← 정상 유효 날짜 확인
```

메시지 번호 `2513`은 CEEDAYS의 "날짜 유효(normal)" 메시지 코드다. 호출자는 심각도 `'0000'`과 메시지 번호 `'2513'`을 **동시에** 확인해 유효성을 판단한다는 점에 주목해야 한다. Java 마이그레이션 시 이 이중 조건을 단순화할 수 있다.
