# CVCUS01Y — 고객 마스터 레코드 레이아웃

- **유형**: Copybook (데이터 구조 정의 전용, 실행 코드 없음)
- **한 줄 요약**: VSAM KSDS 파일 CUSTDATA의 고객 1건(500바이트 고정길이)을 표현하는 `CUSTOMER-RECORD` 레이아웃을 정의한다.

---

## 기능 설명

`CVCUS01Y`는 CardDemo 시스템에서 고객 엔티티의 규범적(canonical) 데이터 스키마를 제공하는 순수 데이터 copybook이다.
실제 실행 로직은 전혀 포함하지 않으며, 온라인(CICS) 프로그램과 배치 프로그램이 `COPY CVCUS01Y`를 통해 동일한 레코드 구조를 공유하는 공유 DTO(Data Transfer Object) 역할을 한다.

레코드 전체 길이는 주석에서 명시된 대로 500바이트(RECLN 500)이며, 식별·연락처·신원확인·신용 정보 필드들로 구성된다.
마지막 168바이트는 `FILLER`로 예약되어 있어 향후 필드 추가 여지를 남겨 두고 있다.

Java 관점에서는 이 copybook이 `CustomerRecord` POJO(또는 JPA `@Entity`) 클래스와 대응되며, 모든 고객 관련 서비스가 공유하는 공통 모델 클래스에 해당한다.

---

## 필드 레이아웃

레코드 루트: `01 CUSTOMER-RECORD` (행 4)

| 레벨 | 필드명 | PIC / USAGE | 바이트 | 의미 및 Java 매핑 |
|------|--------|-------------|--------|-------------------|
| 05 | `CUST-ID` | `PIC 9(09)` | 9 | 고객 고유 식별자. DISPLAY 형식 순수 숫자 9자리. Java: `long` 또는 `String` (선행 0 보존 필요 시). CUSTDATA VSAM KSDS의 **기본 키(Primary Key)**로 사용된다. |
| 05 | `CUST-FIRST-NAME` | `PIC X(25)` | 25 | 이름. 우측 공백 패딩 고정길이 문자열. Java: `String` (trim() 필수). |
| 05 | `CUST-MIDDLE-NAME` | `PIC X(25)` | 25 | 중간 이름. 미국식 미들 네임; 없으면 공백 패딩. Java: `String` (nullable로 처리 권장). |
| 05 | `CUST-LAST-NAME` | `PIC X(25)` | 25 | 성(Last Name). Java: `String` (trim() 필수). |
| 05 | `CUST-ADDR-LINE-1` | `PIC X(50)` | 50 | 주소 1행. Java: `String`. |
| 05 | `CUST-ADDR-LINE-2` | `PIC X(50)` | 50 | 주소 2행. 아파트 호수 등 보조 주소. Java: `String` (nullable). |
| 05 | `CUST-ADDR-LINE-3` | `PIC X(50)` | 50 | 주소 3행. 추가 주소 정보. Java: `String` (nullable). |
| 05 | `CUST-ADDR-STATE-CD` | `PIC X(02)` | 2 | 미국 주(State) 2자리 코드 (예: `CA`, `NY`). Java: `String` (2자). |
| 05 | `CUST-ADDR-COUNTRY-CD` | `PIC X(03)` | 3 | 국가 코드 3자리 (ISO 3166-1 alpha-3 추측, 예: `USA`). Java: `String` (3자). |
| 05 | `CUST-ADDR-ZIP` | `PIC X(10)` | 10 | 우편번호. 영숫자 혼합 허용(`PIC X`)으로 ZIP+4(`12345-6789`) 또는 캐나다식 포맷도 수용. Java: `String`. |
| 05 | `CUST-PHONE-NUM-1` | `PIC X(15)` | 15 | 주 전화번호. 구분자 포함 텍스트 형식 (예: `555-123-4567   `). Java: `String` (trim()). |
| 05 | `CUST-PHONE-NUM-2` | `PIC X(15)` | 15 | 보조 전화번호. Java: `String` (nullable). |
| 05 | `CUST-SSN` | `PIC 9(09)` | 9 | 미국 사회보장번호(SSN). DISPLAY 형식 숫자 9자리. Java: `String` (선행 0 보존 및 PII 마스킹 필수). **민감 개인정보(PII) — 암호화 또는 토큰화 권장.** |
| 05 | `CUST-GOVT-ISSUED-ID` | `PIC X(20)` | 20 | 정부 발급 신분증 번호 (운전면허 등). Java: `String`. PII 해당. |
| 05 | `CUST-DOB-YYYY-MM-DD` | `PIC X(10)` | 10 | 생년월일. `YYYY-MM-DD` 형식의 텍스트 날짜 (`PIC X`, 숫자형 아님). Java: `LocalDate.parse(value.trim())` 으로 변환. |
| 05 | `CUST-EFT-ACCOUNT-ID` | `PIC X(10)` | 10 | EFT(전자자금이체) 연결 계좌 ID. 외부 결제망 계좌 식별자. Java: `String`. |
| 05 | `CUST-PRI-CARD-HOLDER-IND` | `PIC X(01)` | 1 | 주 카드 소지자 여부 플래그. 단일 문자 (`Y`/`N` 추측). Java: `boolean` 또는 `char`. Level-88 조건명은 정의되어 있지 않다. |
| 05 | `CUST-FICO-CREDIT-SCORE` | `PIC 9(03)` | 3 | FICO 신용점수. DISPLAY 형식 3자리 정수 (범위 300~850). Java: `int`. |
| 05 | `FILLER` | `PIC X(168)` | 168 | 예약 패딩. 현재 사용되지 않는 공간. Java 매핑 불필요. |

**레코드 합계 검증:**
9 + 25 + 25 + 25 + 50 + 50 + 50 + 2 + 3 + 10 + 15 + 15 + 9 + 20 + 10 + 10 + 1 + 3 + 168 = **500바이트** ✓ (주석 RECLN 500과 일치, 행 2)

---

## 의존성

- **COPY (중첩 카피북)**: 없음. 이 copybook은 다른 copybook을 `COPY`하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음. 데이터 정의만 포함하므로 호출 관계 없음.
- **데이터셋/파일/DB 테이블**: VSAM KSDS 파일 `CUSTDATA` (레코드 길이 500). 기본 키는 `CUST-ID`(9자리 숫자). 온라인 프로그램은 `CUSTFILE`(DD명), 배치 프로그램은 `CUSTFILE` 또는 `CUSTOMER-FILE`(JCL DD명)로 이 파일에 접근한다.
- **트랜잭션 ID 또는 EXEC PGM**: 없음.

이 copybook을 `COPY`하는 주요 프로그램(참고용):
- 온라인: `COCRDLIC`, `COACTUPC`, `COCRDSLC` 등 고객 조회·수정 화면 프로그램
- 배치: `CBCUS01C`(고객 파일 처리), `CBTRN02C`(거래 처리에서 고객 정보 조회)

---

## Java/현대화 노트

### 1. Java POJO 매핑 예시

```java
public class CustomerRecord {
    // PIC 9(09) — DISPLAY, 9바이트. VSAM 기본 키.
    private long custId;                   // 또는 String: "000000001" 형태 보존 시

    private String custFirstName;          // PIC X(25), trim() 필수
    private String custMiddleName;         // PIC X(25), nullable
    private String custLastName;           // PIC X(25), trim() 필수

    private String custAddrLine1;          // PIC X(50)
    private String custAddrLine2;          // PIC X(50), nullable
    private String custAddrLine3;          // PIC X(50), nullable
    private String custAddrStateCd;        // PIC X(02)
    private String custAddrCountryCd;      // PIC X(03)
    private String custAddrZip;            // PIC X(10)

    private String custPhoneNum1;          // PIC X(15), trim() 필수
    private String custPhoneNum2;          // PIC X(15), nullable

    // PIC 9(09) — PII. String으로 보존 (선행 0 유지) + 암호화 권장
    private String custSsn;

    private String custGovtIssuedId;       // PIC X(20), PII
    private LocalDate custDob;             // PIC X(10) "YYYY-MM-DD" → LocalDate
    private String custEftAccountId;       // PIC X(10)
    private boolean custPriCardHolderInd;  // PIC X(01) 'Y'/'N'
    private int custFicoCreditScore;       // PIC 9(03), 범위 300-850
}
```

### 2. 고정길이 문자열 처리

COBOL `PIC X(n)` 필드는 항상 n바이트 고정이며 우측을 공백으로 패딩한다. Java로 변환 시 반드시 `String.trim()` 또는 `StringUtils.trimRight()`를 적용해야 한다. 그렇지 않으면 "HONG      " 같은 후행 공백이 비교·검색 오류를 유발한다.

### 3. EBCDIC vs ASCII

메인프레임에서 이 레코드는 EBCDIC 인코딩으로 저장된다. AWS Mainframe Modernization 환경 또는 파일 다운로드 시 반드시 EBCDIC→UTF-8(또는 ASCII) 변환을 거쳐야 한다. `PIC 9` 필드도 EBCDIC DISPLAY 형식(존 십진수, Zoned Decimal)이므로 단순 바이트 복사로는 틀린 값이 된다.

### 4. CUST-SSN 및 PII 보안

`CUST-SSN`(행 17)과 `CUST-GOVT-ISSUED-ID`(행 18)는 개인식별정보(PII)다. COBOL 원본에는 암호화 없이 DISPLAY 텍스트로 저장된다. Java 마이그레이션 시 반드시 암호화(AES-256), 토큰화, 또는 마스킹 처리를 추가해야 한다. `@Column(name = "cust_ssn")` 단독 사용은 충분하지 않다.

### 5. 생년월일 변환

`CUST-DOB-YYYY-MM-DD`(행 19)는 `PIC X(10)` 즉 순수 텍스트다. 숫자형(`PIC 9`)이 아니므로 날짜 계산이 불가하다. Java에서는 `LocalDate.parse(value.trim())` (ISO_LOCAL_DATE 포맷 기본 적용)으로 변환하고 이후 `LocalDate`로 관리하는 것이 안전하다.

### 6. FILLER 168바이트

행 23의 `FILLER PIC X(168)`은 현재 사용하지 않는 예약 공간이다. Java 매핑에서는 무시해도 되지만, 고정길이 바이너리 파일을 직접 파싱하는 경우에는 오프셋 계산 시 반드시 포함해야 500바이트 경계가 맞는다.

### 7. VSAM KSDS vs JPA

VSAM KSDS는 키 기반 랜덤 접근을 지원하는 파일 시스템 구조다. Java 마이그레이션 시 일반적으로 RDBMS 테이블(`customers`)로 대체되고, `CUST-ID`가 `PRIMARY KEY`가 된다. JPA 사용 시 `@Entity @Table(name = "customers")` + `@Id @Column(name = "cust_id")` 패턴을 적용한다.

```java
@Entity
@Table(name = "customers")
public class CustomerRecord {
    @Id
    @Column(name = "cust_id")
    private long custId;
    // ... 나머지 필드
}
```

### 8. 카드·계정 연계

고객 레코드 자체에는 카드번호나 계정 ID가 포함되지 않는다. 연결은 `CVACT03Y`의 `CARD-XREF-RECORD`(`XREF-CUST-ID` → `XREF-ACCT-ID` → `XREF-CARD-NUM`)를 통해 이루어진다. Java JPA 설계 시 `CustomerRecord`와 `CardXrefRecord` 사이에 `@OneToMany` 관계를 설정해야 한다.

---

*버전: CardDemo_v1.0-15-g27d6c6f-68 / 2022-07-19 23:16:00 CDT (소스 행 25)*
