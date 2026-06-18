# CSDAT01Y — 화면 공통 날짜/시간 표시 구조체

- **유형**: Copybook (WORKING-STORAGE 공용 데이터 정의)
- **한 줄 요약**: 거의 모든 CO* 온라인 프로그램이 COPY하는 공통 날짜·시간 저장소로, 원시 숫자 컴포넌트, 슬래시 구분 표시용 문자열(`MM/DD/YY`), 콜론 구분 표시용 문자열(`HH:MM:SS`), ISO-8601 스타일 타임스탬프(`YYYY-MM-DD HH:MM:SS.microseconds`) 네 가지 뷰를 단일 01 그룹 아래에 제공한다.

---

## 기능 설명

`CSDAT01Y`는 **화면 헤더 날짜·시간 표시**에 특화된 공통 레코드 정의이다. CO* 온라인 프로그램들은 매 pseudo-conversational 턴에서 `EXEC CICS ASKTIME / FORMATTIME` 결과를 이 구조체에 채운 뒤 BMS 화면 맵에 복사해 헤더 행에 날짜와 시간을 출력한다(메모리 내 `online_pseudoconv_pattern.md`의 `POPULATE-HEADER-INFO` 단락).

구조체는 네 개의 05-level 그룹으로 이루어진다.

1. **`WS-CURDATE-DATA`** — 원시 날짜·시간 숫자 컴포넌트 + 단일-정수 REDEFINES 두 개  
2. **`WS-CURDATE-MM-DD-YY`** — 슬래시(`/`)로 채워진 `MM/DD/YY` 표시 문자열 (6자리 + 구분자 2개 = 8바이트)  
3. **`WS-CURTIME-HH-MM-SS`** — 콜론(`:`)으로 채워진 `HH:MM:SS` 표시 문자열 (6자리 + 구분자 2개 = 8바이트)  
4. **`WS-TIMESTAMP`** — `YYYY-MM-DD HH:MM:SS.mmmmmm` 형식의 19+7=26바이트 타임스탬프 (공백·하이픈·콜론·점 FILLER 포함)

`FILLER` 필드에 `VALUE '/'`, `VALUE ':'` 등의 리터럴이 박혀 있으므로, COBOL 프로그램은 숫자 컴포넌트만 MOVE하면 구분자가 자동으로 포함된 표시용 문자열을 얻는다. Java로 치면 `String.format("%02d/%02d/%02d", month, day, year % 100)` 결과물을 미리 만들어두는 것과 같다.

---

## 필드 레이아웃

> 소스 라인 17–55 기준. 들여쓰기 수준(레벨)은 COBOL 레벨번호를 따른다.

### 01 WS-DATE-TIME (전체 구조 루트)

| 필드명 | PIC / USAGE | 바이트 | 의미 |
|---|---|---|---|
| **05 WS-CURDATE-DATA** | (그룹) | 16 | 원시 날짜+시간 컴포넌트 묶음 |
| ‥ 10 WS-CURDATE | (그룹) | 8 | 연/월/일 묶음 |
| ‥ ‥ 15 WS-CURDATE-YEAR | PIC 9(04) DISPLAY | 4 | 연도 4자리 (예: 2022) |
| ‥ ‥ 15 WS-CURDATE-MONTH | PIC 9(02) DISPLAY | 2 | 월 2자리 (01–12) |
| ‥ ‥ 15 WS-CURDATE-DAY | PIC 9(02) DISPLAY | 2 | 일 2자리 (01–31) |
| ‥ 10 WS-CURDATE-N | PIC 9(08) DISPLAY (**REDEFINES WS-CURDATE**) | 8 | 날짜를 `YYYYMMDD` 정수 하나로 접근할 때 사용. 동일 메모리 재해석 |
| ‥ 10 WS-CURTIME | (그룹) | 8 | 시/분/초/밀리초 묶음 |
| ‥ ‥ 15 WS-CURTIME-HOURS | PIC 9(02) DISPLAY | 2 | 시 (00–23) |
| ‥ ‥ 15 WS-CURTIME-MINUTE | PIC 9(02) DISPLAY | 2 | 분 (00–59) |
| ‥ ‥ 15 WS-CURTIME-SECOND | PIC 9(02) DISPLAY | 2 | 초 (00–59) |
| ‥ ‥ 15 WS-CURTIME-MILSEC | PIC 9(02) DISPLAY | 2 | 밀리초 앞 2자리(00–99). CICS `ASKTIME` 결과는 10ms 단위 |
| ‥ 10 WS-CURTIME-N | PIC 9(08) DISPLAY (**REDEFINES WS-CURTIME**) | 8 | 시간을 `HHMMSScc` 정수 하나로 접근할 때 사용 |
| **05 WS-CURDATE-MM-DD-YY** | (그룹) | 8 | `MM/DD/YY` 표시용 문자열 (FILLER에 '/' 내장) |
| ‥ 10 WS-CURDATE-MM | PIC 9(02) DISPLAY | 2 | 월 표시부분 |
| ‥ 10 FILLER | PIC X(01) VALUE '/' | 1 | 슬래시 구분자 |
| ‥ 10 WS-CURDATE-DD | PIC 9(02) DISPLAY | 2 | 일 표시부분 |
| ‥ 10 FILLER | PIC X(01) VALUE '/' | 1 | 슬래시 구분자 |
| ‥ 10 WS-CURDATE-YY | PIC 9(02) DISPLAY | 2 | 연도 하위 2자리 표시부분 (2000년대 기준 `year % 100`) |
| **05 WS-CURTIME-HH-MM-SS** | (그룹) | 8 | `HH:MM:SS` 표시용 문자열 (FILLER에 ':' 내장) |
| ‥ 10 WS-CURTIME-HH | PIC 9(02) DISPLAY | 2 | 시 표시부분 |
| ‥ 10 FILLER | PIC X(01) VALUE ':' | 1 | 콜론 구분자 |
| ‥ 10 WS-CURTIME-MM | PIC 9(02) DISPLAY | 2 | 분 표시부분 |
| ‥ 10 FILLER | PIC X(01) VALUE ':' | 1 | 콜론 구분자 |
| ‥ 10 WS-CURTIME-SS | PIC 9(02) DISPLAY | 2 | 초 표시부분 |
| **05 WS-TIMESTAMP** | (그룹) | 26 | `YYYY-MM-DD HH:MM:SS.mmmmmm` ISO 스타일 타임스탬프 |
| ‥ 10 WS-TIMESTAMP-DT-YYYY | PIC 9(04) DISPLAY | 4 | 연도 4자리 |
| ‥ 10 FILLER | PIC X(01) VALUE '-' | 1 | 하이픈 |
| ‥ 10 WS-TIMESTAMP-DT-MM | PIC 9(02) DISPLAY | 2 | 월 |
| ‥ 10 FILLER | PIC X(01) VALUE '-' | 1 | 하이픈 |
| ‥ 10 WS-TIMESTAMP-DT-DD | PIC 9(02) DISPLAY | 2 | 일 |
| ‥ 10 FILLER | PIC X(01) VALUE ' ' | 1 | 날짜-시간 구분 공백 (ISO 8601의 'T' 대신 공백 사용) |
| ‥ 10 WS-TIMESTAMP-TM-HH | PIC 9(02) DISPLAY | 2 | 시 |
| ‥ 10 FILLER | PIC X(01) VALUE ':' | 1 | 콜론 |
| ‥ 10 WS-TIMESTAMP-TM-MM | PIC 9(02) DISPLAY | 2 | 분 |
| ‥ 10 FILLER | PIC X(01) VALUE ':' | 1 | 콜론 |
| ‥ 10 WS-TIMESTAMP-TM-SS | PIC 9(02) DISPLAY | 2 | 초 |
| ‥ 10 FILLER | PIC X(01) VALUE '.' | 1 | 소수점 |
| ‥ 10 WS-TIMESTAMP-TM-MS6 | PIC 9(06) DISPLAY | 6 | 마이크로초 6자리 (CICS ASKTIME 해상도는 10ms이므로 하위 자리 0으로 채워짐, 추측) |

**총 메모리**: 16 (WS-CURDATE-DATA) + 8 (MM-DD-YY) + 8 (HH-MM-SS) + 26 (TIMESTAMP) = **58바이트**

**REDEFINES 주의사항**:
- `WS-CURDATE-N REDEFINES WS-CURDATE` — WS-CURDATE(YEAR 4B + MONTH 2B + DAY 2B = 8B)와 동일 오프셋의 8자리 숫자. 숫자 비교나 정수 연산 시 편리하나, Java에는 직접 대응되는 개념이 없다. Java에서는 `int dateInt = year * 10000 + month * 100 + day;`로 동일 효과를 표현한다.
- `WS-CURTIME-N REDEFINES WS-CURTIME` — 마찬가지로 8자리 `HHMMSScc` 정수 뷰.

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — 이 copybook 자체가 다른 copybook을 COPY하지 않는다
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음
- **데이터셋/파일/DB 테이블**: 없음 — 순수 메모리 구조체
- **트랜잭션 ID 또는 EXEC PGM**: 없음

---

## Java/현대화 노트

### 1. 직접 대응 Java 클래스

```java
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CSDAT01Y WS-DATE-TIME 의 Java 대응 — CardDemo 화면 헤더 날짜/시간
 */
public class ScreenDateTime {

    // WS-CURDATE-DATA / WS-CURDATE
    private int year;        // WS-CURDATE-YEAR  PIC 9(04)
    private int month;       // WS-CURDATE-MONTH PIC 9(02)
    private int day;         // WS-CURDATE-DAY   PIC 9(02)
    // WS-CURDATE-N (REDEFINES) → 아래 메서드로 대체
    public int getCurdateN() { return year * 10000 + month * 100 + day; }

    // WS-CURTIME
    private int hours;       // WS-CURTIME-HOURS   PIC 9(02)
    private int minute;      // WS-CURTIME-MINUTE  PIC 9(02)
    private int second;      // WS-CURTIME-SECOND  PIC 9(02)
    private int milsec;      // WS-CURTIME-MILSEC  PIC 9(02) (10ms 단위)
    // WS-CURTIME-N (REDEFINES) → 아래 메서드로 대체
    public int getCurtimeN() { return hours * 1000000 + minute * 10000 + second * 100 + milsec; }

    // WS-CURDATE-MM-DD-YY 표시용 (FILLER '/' 포함 문자열)
    public String getDisplayDate() {
        return String.format("%02d/%02d/%02d", month, day, year % 100);
    }

    // WS-CURTIME-HH-MM-SS 표시용
    public String getDisplayTime() {
        return String.format("%02d:%02d:%02d", hours, minute, second);
    }

    // WS-TIMESTAMP (ISO 스타일, 공백 구분)
    public String getTimestamp() {
        return String.format("%04d-%02d-%02d %02d:%02d:%02d.%06d",
            year, month, day, hours, minute, second, milsec * 10000L);
    }

    /** EXEC CICS ASKTIME / FORMATTIME 결과로 한 번에 초기화 */
    public static ScreenDateTime fromNow() {
        LocalDateTime now = LocalDateTime.now();
        ScreenDateTime dt = new ScreenDateTime();
        dt.year   = now.getYear();
        dt.month  = now.getMonthValue();
        dt.day    = now.getDayOfMonth();
        dt.hours  = now.getHour();
        dt.minute = now.getMinute();
        dt.second = now.getSecond();
        dt.milsec = now.getNano() / 10_000_000; // 10ms 단위로 내림
        return dt;
    }
}
```

### 2. 마이그레이션 시 주의점

| COBOL 특성 | Java 대응 | 주의사항 |
|---|---|---|
| `FILLER VALUE '/'` 내장 구분자 | `String.format()`의 리터럴 | COBOL에서는 구분자가 데이터와 같은 메모리에 물리적으로 존재. Java에서는 포맷팅 시 생성 |
| `WS-CURDATE-YY` = 연도 하위 2자리 | `year % 100` | 2099년 이후 문제 없음(표시 전용이므로). 그러나 로직 비교에 2자리 연도를 쓰면 Y2K 재현 주의 |
| `WS-CURTIME-MILSEC` = 10ms 단위 2자리 | `nanos / 10_000_000` | CICS ASKTIME 해상도는 실제 10ms이므로 하위 4자리는 항상 `0000` (추측). Java `System.nanoTime()` 이용 시 과도한 정밀도 발생 |
| `WS-TIMESTAMP-TM-MS6` = 6자리 | `milsec * 10000` | COBOL에서는 CICS 10ms 해상도를 6자리에 채우면 10ms * 10000 = 정수 배수만 가능. 실제 마이크로초가 아님 |
| `REDEFINES` 두 개 | 별도 getter | Java Union 타입 없음. 동일 데이터를 다른 형태로 읽는 메서드로 표현 |
| PIC 9(n) DISPLAY | `int` / `String` | DISPLAY 숫자는 EBCDIC 환경에서 각 자리가 1바이트. ASCII Java `String`으로 읽을 때 `Cp1047` → UTF-8 변환 확인 필요 |

### 3. 사용 패턴 (CO* 프로그램 공통)

모든 CO* 화면 프로그램의 `POPULATE-HEADER-INFO` (또는 동명 단락)에서 아래 패턴이 반복된다:

```cobol
EXEC CICS ASKTIME ABSTIME(WS-ABSTIME) END-EXEC
EXEC CICS FORMATTIME ABSTIME(WS-ABSTIME)
          YYYYMMDD(WS-CURDATE)
          TIME(WS-CURTIME)
          END-EXEC
MOVE WS-CURDATE-YEAR  TO WS-CURDATE-MM-DD-YY ... (또는 직접 FORMATTIME 출력 사용)
```

Java Spring MVC 환경에서는 `@ModelAttribute` 또는 Thymeleaf `${#dates.format(now,'MM/dd/yy')}`로 대체한다.

### 4. ISO 8601 vs COBOL 타임스탬프 형식 차이

`WS-TIMESTAMP`는 날짜와 시간을 `T` 대신 **공백**으로 구분한다. DB2/JDBC `TIMESTAMP` 리터럴 형식(`'YYYY-MM-DD HH:MM:SS'`)과는 일치하지만, 엄밀한 ISO 8601(`2022-07-19T23:15:58`)과는 다르다. REST API 응답 등에 노출 시 `T` 구분자 및 타임존(`Z` 또는 `+09:00`) 추가가 필요하다.
