# CUSTREC — 고객 레코드 레이아웃 (명세서 생성 배치 전용)

- **유형**: Copybook
- **한 줄 요약**: 명세서 생성 배치 CBSTM03A가 `COPY CUSTREC`으로 가져오는 고객 엔티티 레이아웃(500바이트 고정길이)으로, 규범적 스키마 `CVCUS01Y`와 필드 정의는 동일하지만 `CUST-DOB` 필드명 표기 방식이 다른 사실상(de facto) 복사본이다.

---

## 기능 설명

`CUSTREC`은 CardDemo 시스템에서 고객 1건의 데이터 구조를 정의하는 순수 데이터 copybook이다.
실행 코드는 전혀 없고, `CUSTOMER-RECORD`라는 01레벨 레이아웃만 제공한다.

현재 이 copybook을 `COPY`하는 프로그램은 **CBSTM03A.CBL 단 한 개**다(소스 55행).
CBSTM03A는 고객 정보를 명세서 텍스트와 HTML 두 형식으로 출력할 때 이 레이아웃을 사용한다.
구체적으로, CBSTM03B 서브루틴이 CUSTFILE(VSAM KSDS)에서 반환한 1000바이트 범용 버퍼(`WS-M03B-FLDT`)를 `MOVE WS-M03B-FLDT TO CUSTOMER-RECORD`로 복사한 뒤, `CUST-FIRST-NAME`, `CUST-LAST-NAME`, `CUST-ADDR-LINE-1`~`LINE-3`, `CUST-ADDR-STATE-CD`, `CUST-ADDR-COUNTRY-CD`, `CUST-ADDR-ZIP`, `CUST-FICO-CREDIT-SCORE` 필드를 STRING/MOVE로 명세서 출력 라인에 배치한다(CBSTM03A 388·462~485행).

Java 관점에서 이 copybook은 `CustomerRecord` DTO 클래스에 해당하며, I/O 서브루틴(CBSTM03B = DAO 파사드)이 반환하는 byte array를 역직렬화한 결과 객체로 볼 수 있다.

---

## 필드 레이아웃

레코드 루트: `01 CUSTOMER-RECORD` (소스 4행)

| 레벨 | 필드명 | PIC / USAGE | 바이트 | 오프셋 | 의미 및 Java 매핑 |
|------|--------|-------------|--------|--------|-------------------|
| 05 | `CUST-ID` | `PIC 9(09)` | 9 | 0 | 고객 고유 식별자. DISPLAY(존 십진수) 형식 9자리. CUSTFILE VSAM KSDS 기본 키. Java: `long` 또는 `String`(선행 0 보존 시). |
| 05 | `CUST-FIRST-NAME` | `PIC X(25)` | 25 | 9 | 이름. 우측 공백 패딩 고정길이 알파뉴메릭. Java: `String`, `trim()` 필수. |
| 05 | `CUST-MIDDLE-NAME` | `PIC X(25)` | 25 | 34 | 중간 이름. 없으면 공백 패딩. Java: `String`, null 처리 권장. |
| 05 | `CUST-LAST-NAME` | `PIC X(25)` | 25 | 59 | 성(Last Name). Java: `String`, `trim()` 필수. |
| 05 | `CUST-ADDR-LINE-1` | `PIC X(50)` | 50 | 84 | 주소 1행. Java: `String`. |
| 05 | `CUST-ADDR-LINE-2` | `PIC X(50)` | 50 | 134 | 주소 2행. 아파트 호수 등 보조 주소. Java: `String`, nullable. |
| 05 | `CUST-ADDR-LINE-3` | `PIC X(50)` | 50 | 184 | 주소 3행. 추가 주소 정보. Java: `String`, nullable. |
| 05 | `CUST-ADDR-STATE-CD` | `PIC X(02)` | 2 | 234 | 미국 주(State) 2자리 코드 (예: `CA`, `NY`). Java: `String` (2자). |
| 05 | `CUST-ADDR-COUNTRY-CD` | `PIC X(03)` | 3 | 236 | 국가 코드 3자리 (ISO 3166-1 alpha-3 추측, 예: `USA`). Java: `String` (3자). |
| 05 | `CUST-ADDR-ZIP` | `PIC X(10)` | 10 | 239 | 우편번호. `PIC X`이므로 영숫자 혼합 허용 (ZIP+4, 캐나다식 등). Java: `String`. |
| 05 | `CUST-PHONE-NUM-1` | `PIC X(15)` | 15 | 249 | 주 전화번호. 구분자 포함 텍스트 형식. Java: `String`, `trim()` 필수. |
| 05 | `CUST-PHONE-NUM-2` | `PIC X(15)` | 15 | 264 | 보조 전화번호. Java: `String`, nullable. |
| 05 | `CUST-SSN` | `PIC 9(09)` | 9 | 279 | 미국 사회보장번호(SSN). DISPLAY 형식 9자리. **PII — 암호화/토큰화 필수.** Java: `String`(선행 0 보존). |
| 05 | `CUST-GOVT-ISSUED-ID` | `PIC X(20)` | 20 | 288 | 정부 발급 신분증 번호. **PII 해당.** Java: `String`. |
| 05 | `CUST-DOB-YYYYMMDD` | `PIC X(10)` | 10 | 308 | 생년월일. **이 copybook에서의 필드명 표기.** 값 포맷은 `YYYYMMDD` 또는 `YYYY-MM-DD`인지 불확실(추측 — 실제 데이터 샘플 확인 필요). `PIC X`(순수 텍스트)이므로 날짜 계산 불가. Java: `LocalDate.parse()` 변환 전 포맷 검증 필수. |
| 05 | `CUST-EFT-ACCOUNT-ID` | `PIC X(10)` | 10 | 318 | EFT(전자자금이체) 연결 계좌 ID. Java: `String`. |
| 05 | `CUST-PRI-CARD-HOLDER-IND` | `PIC X(01)` | 1 | 328 | 주 카드 소지자 여부 플래그. 단일 문자 (`Y`/`N` 추측). Level-88 조건명 없음. Java: `boolean` 또는 `char`. |
| 05 | `CUST-FICO-CREDIT-SCORE` | `PIC 9(03)` | 3 | 329 | FICO 신용점수. DISPLAY 형식 3자리 정수 (범위 300~850). Java: `int`. |
| 05 | `FILLER` | `PIC X(168)` | 168 | 332 | 예약 패딩. 현재 미사용. Java 매핑 불필요(단, 고정길이 바이너리 파싱 시 오프셋 계산에 포함 필수). |

**레코드 합계 검증:**
9 + 25 + 25 + 25 + 50 + 50 + 50 + 2 + 3 + 10 + 15 + 15 + 9 + 20 + 10 + 10 + 1 + 3 + 168 = **500바이트** ✓ (주석 RECLN 500과 일치, 소스 2행)

---

## 의존성

- **COPY (중첩 카피북)**: 없음. 이 copybook은 다른 copybook을 `COPY`하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음. 데이터 정의만 포함하므로 호출 관계 없음.
- **데이터셋/파일/DB 테이블**: VSAM KSDS 파일 `CUSTFILE` (JCL DD명). 레코드 길이 500바이트. 기본 키는 `CUST-ID`(9자리 숫자). 이 copybook 사용 프로그램인 CBSTM03A는 파일을 직접 열지 않고, CBSTM03B 서브루틴에게 `WS-M03B-DD = 'CUSTFILE'` + 오퍼레이션 코드로 위임한다.
- **트랜잭션 ID 또는 EXEC PGM**: 없음.

이 copybook을 `COPY`하는 프로그램:
- `CBSTM03A.CBL` (소스 55행) — 명세서 생성 배치 드라이버

---

## Java/현대화 노트

### 1. CVCUS01Y와의 관계 및 차이점

`CUSTREC`과 `CVCUS01Y`는 **동일한 `CUSTOMER-RECORD` 01레벨 구조**를 정의하는 사실상 복사본이다.
두 파일을 `diff`하면 내용 차이는 필드명 하나뿐이다:

| copybook | 생년월일 필드명 | 타임스탬프 |
|----------|----------------|-----------|
| `CUSTREC` | `CUST-DOB-YYYYMMDD` | 2022-07-19 23:15:59 CDT |
| `CVCUS01Y` | `CUST-DOB-YYYY-MM-DD` | 2022-07-19 23:16:00 CDT |

버전 태그는 동일(`CardDemo_v1.0-15-g27d6c6f-68`)하고 타임스탬프 차이는 1초다.
`CVCUS01Y`가 1초 늦게 생성된 것으로 보아 `CUSTREC`에서 필드명 하이픈 추가 후 재생성된 버전이 `CVCUS01Y`일 가능성이 높다(추측).

**Java 마이그레이션 핵심 함의**: 두 copybook이 공존하는 한, CBSTM03A가 읽는 CUSTFILE 레코드와 다른 프로그램(CVCUS01Y 사용)이 읽는 레코드는 바이트 레이아웃이 동일하므로 물리적 호환성은 있다. 그러나 Java DTO 클래스로 통합할 때는 반드시 **하나의 권위적(authoritative) 클래스**만 유지해야 한다. `CVCUS01Y`가 더 최신이므로 `CustomerRecord` 클래스 필드명은 `CVCUS01Y` 기준(`custDob`, 포맷 `YYYY-MM-DD`)을 따르는 것을 권장한다.

### 2. 생년월일 필드명 불일치와 포맷 모호성

`CUST-DOB-YYYYMMDD`라는 이름은 `YYYYMMDD` 8자리 형식을 암시하지만 `PIC X(10)` 즉 10바이트이다.
8바이트 값이 10바이트 필드에 저장되면 나머지 2바이트가 공백 패딩이거나, 실제로는 `YYYY-MM-DD` 포맷(하이픈 포함 10자)으로 저장될 수 있다.
`CVCUS01Y`의 필드명이 `CUST-DOB-YYYY-MM-DD`임을 고려하면 실제 저장 포맷은 `YYYY-MM-DD`(하이픈 포함)일 가능성이 높다(추측 — 실제 CUSTFILE 데이터 샘플 확인 필수).

```java
// 안전한 변환 패턴 — 두 포맷 모두 시도
String raw = custDobRaw.trim();
LocalDate dob;
try {
    // YYYY-MM-DD (10자) 우선 시도
    dob = LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
} catch (DateTimeParseException e) {
    // YYYYMMDD (8자) fallback
    dob = LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE);
}
```

### 3. CBSTM03A 사용 패턴: DAO 파사드 역직렬화

CBSTM03A에서 이 copybook이 사용되는 흐름은 다음과 같다:

```
CBSTM03A
  WS-M03B-DD = 'CUSTFILE'
  WS-M03B-KEY = XREF-CUST-ID    ← CVACT03Y에서 읽은 고객 ID
  WS-M03B-OPER = 'K'            ← 키 기반 읽기
  CALL 'CBSTM03B' USING WS-M03B-AREA
  MOVE WS-M03B-FLDT TO CUSTOMER-RECORD   ← 역직렬화
  → CUST-FIRST-NAME, CUST-LAST-NAME 등 직접 참조
```

Java 등가 패턴:

```java
// CBSTM03B = 범용 DAO 파사드
byte[] raw = fileHandler.read("CUSTFILE", custId);
CustomerRecord customer = CustomerRecord.fromBytes(raw);  // 역직렬화
String fullName = customer.getCustFirstName().trim()
                + " " + customer.getCustLastName().trim();
```

### 4. 고정길이 문자열 처리

모든 `PIC X(n)` 필드는 n바이트 고정이며 우측을 공백으로 패딩한다.
Java 변환 시 `String.trim()` 또는 Apache Commons의 `StringUtils.stripEnd(s, " ")`를 반드시 적용해야 한다.
`CUST-FIRST-NAME = "HONG             "` 같은 후행 공백이 비교·해시 오류를 유발한다.

### 5. EBCDIC vs ASCII

CUSTFILE은 메인프레임에서 EBCDIC 인코딩으로 저장된다.
`PIC 9` 필드(`CUST-ID`, `CUST-SSN`, `CUST-FICO-CREDIT-SCORE`)는 DISPLAY(존 십진수, Zoned Decimal) 형식으로,
EBCDIC에서 숫자 '0'~'9'는 `0xF0`~`0xF9`로 인코딩된다.
단순 `new String(bytes, "UTF-8")` 변환은 틀린 값을 만든다. AWS Mainframe Modernization 환경에서는 EBCDIC→UTF-8 변환 레이어를 반드시 거쳐야 한다.

### 6. PII 필드 보안

`CUST-SSN`(오프셋 279, 9바이트)과 `CUST-GOVT-ISSUED-ID`(오프셋 288, 20바이트)는 PII다.
COBOL 원본에는 암호화 없이 DISPLAY 텍스트로 저장된다.
Java 마이그레이션 시 반드시 암호화(AES-256), 토큰화, 또는 컬럼 레벨 암호화를 적용해야 한다.

### 7. 두 copybook 병행 사용의 리스크

현재 코드베이스에는 동일 레이아웃을 정의하는 copybook이 두 개(`CUSTREC`, `CVCUS01Y`) 존재한다.
이는 미래에 한쪽만 수정되면 레이아웃 불일치가 발생할 수 있는 잠재적 위험이다.
**권장**: Java 마이그레이션 시 `CVCUS01Y` 기준 단일 `CustomerRecord` 클래스로 통합하고, CBSTM03A 포팅 코드도 동일 클래스를 사용하도록 통일한다.

### 8. 카드·계정 연계

고객 레코드 자체에는 카드번호나 계정 ID가 포함되지 않는다.
연결은 `CVACT03Y`의 `CARD-XREF-RECORD`(`XREF-CUST-ID` → `XREF-ACCT-ID` → `XREF-CARD-NUM`)를 통해 이루어진다.
Java JPA 설계 시 `CustomerRecord`와 `CardXrefRecord` 사이에 `@OneToMany` 관계를 설정해야 한다.

---

*버전: CardDemo_v1.0-15-g27d6c6f-68 / 2022-07-19 23:15:59 CDT (소스 25행)*
*참고: 규범적 스키마 `CVCUS01Y` 문서는 `docs/cpy/CVCUS01Y.md` 참조*
