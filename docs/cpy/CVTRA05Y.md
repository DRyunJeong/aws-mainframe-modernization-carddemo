# CVTRA05Y — 거래 마스터 레코드 레이아웃

- **유형**: Copybook (VSAM KSDS 레코드 스키마)
- **한 줄 요약**: TRANSACT VSAM KSDS 파일의 350바이트 거래(Transaction) 마스터 레코드 구조를 정의하는 공유 데이터 스키마 copybook.

---

## 기능 설명

`CVTRA05Y`는 CardDemo 시스템의 핵심 거래 파일인 `TRANSACT` VSAM KSDS(Key-Sequenced Data Set)에 저장되는 레코드 레이아웃을 선언한다. 레코드 길이는 350바이트(`RECLN = 350`)이며, 온라인 CICS 프로그램과 배치 프로그램이 이 copybook을 공통으로 `COPY`하여 동일한 필드명과 오프셋을 공유한다.

Java의 관점에서는 여러 서비스 클래스가 함께 사용하는 `Transaction.java` DTO(Data Transfer Object) 또는 JPA `@Entity` 클래스에 해당한다. COBOL에서는 별도 파일로 정의된 이 레이아웃을 컴파일 시점에 `COPY` 문으로 인라인 삽입하므로, 모든 사용 프로그램이 동일한 필드 오프셋을 보장받는다.

---

## 필드 레이아웃

`01 TRAN-RECORD` (총 350바이트)

| 필드명 | PIC / USAGE | 바이트 | 의미 및 Java 매핑 |
|---|---|---|---|
| `TRAN-ID` | `PIC X(16)` | 16 | 거래 고유 식별자. 영숫자 문자열. → `String` (16자 고정, trim 필요) |
| `TRAN-TYPE-CD` | `PIC X(02)` | 2 | 거래 유형 코드 (예: `PR`=구매, `RF`=환불 등). `CVTRA03Y` 참조파일의 키. → `String` (2자) |
| `TRAN-CAT-CD` | `PIC 9(04)` | 4 | 거래 카테고리 코드 (숫자 4자리). `CVTRA04Y` 참조파일의 키. DISPLAY 저장. → `int` 또는 `String` |
| `TRAN-SOURCE` | `PIC X(10)` | 10 | 거래 발생 채널/출처 (예: `POS`, `ONLINE` 등). → `String` (10자 고정, trim 필요) |
| `TRAN-DESC` | `PIC X(100)` | 100 | 거래 설명 자유 텍스트. → `String` (100자 고정, trim 필요) |
| `TRAN-AMT` | `PIC S9(09)V99` | 11 | 거래 금액. 부호 포함(S), 정수 9자리 + 소수점 이하 2자리(V99). DISPLAY 저장. → `BigDecimal` (scale=2 필수) |
| `TRAN-MERCHANT-ID` | `PIC 9(09)` | 9 | 가맹점 식별자 (숫자 9자리). → `long` 또는 `String` |
| `TRAN-MERCHANT-NAME` | `PIC X(50)` | 50 | 가맹점 상호명. → `String` (50자 고정, trim 필요) |
| `TRAN-MERCHANT-CITY` | `PIC X(50)` | 50 | 가맹점 소재 도시명. → `String` (50자 고정, trim 필요) |
| `TRAN-MERCHANT-ZIP` | `PIC X(10)` | 10 | 가맹점 우편번호. → `String` (10자 고정, trim 필요) |
| `TRAN-CARD-NUM` | `PIC X(16)` | 16 | 거래에 사용된 카드 번호. `CARDDATA` VSAM 파일(CVACT02Y)의 키 참조. → `String` (16자 고정) |
| `TRAN-ORIG-TS` | `PIC X(26)` | 26 | 거래 원래 발생 타임스탬프 (문자열 형식, 예: `YYYY-MM-DD HH:MM:SS.UUUUUU`). → `String` 또는 `LocalDateTime` (파싱 후) |
| `TRAN-PROC-TS` | `PIC X(26)` | 26 | 거래 처리 완료 타임스탬프 (동일 형식). → `String` 또는 `LocalDateTime` (파싱 후) |
| `FILLER` | `PIC X(20)` | 20 | 향후 확장 예비 공간. Java 매핑 불필요 (무시 또는 `byte[]` 보관). |

**바이트 합계 검증**: 16+2+4+10+100+11+9+50+50+10+16+26+26+20 = **350** ✓

### TRAN-AMT 저장 방식 주의

`PIC S9(09)V99`는 DISPLAY(EBCDIC 존 십진수) 저장이다. 11자리 숫자를 EBCDIC 문자로 한 바이트씩 저장하며, 부호는 마지막 바이트의 존(zone) 니블에 내장된다(`C`=양수, `D`=음수). `V`는 소수점이 물리적으로 저장되지 않는 묵시적 소수점 위치를 나타낸다. Java 변환 시 반드시 `BigDecimal`을 사용해야 하며, `double`/`float`은 부동소수점 정밀도 손실로 금액 계산 오류를 유발한다.

```java
// COBOL PIC S9(09)V99 (DISPLAY) -> Java 변환 예시
String rawAmt = "00000012345";   // EBCDIC->ASCII 변환 후 11자리 문자열 (부호처리 완료 가정)
BigDecimal amount = new BigDecimal(rawAmt).movePointLeft(2);  // scale 2 적용 -> 123.45
```

---

## 의존성

- **COPY (중첩 카피북)**: 없음. `CVTRA05Y`는 다른 copybook을 `COPY`하지 않는다.
- **호출 프로그램 (CALL/XCTL/LINK)**: 없음. Copybook은 데이터 정의만 포함하며 실행 코드가 없다.
- **데이터셋/파일/DB 테이블**: `TRANSACT` (VSAM KSDS, 레코드 길이 350). `TRAN-ID`가 KSDS의 기본 키(primary key). 이 레코드를 사용하는 배치 프로그램들은 `CBTRN01C`, `CBTRN02C`, `CBTRN03C`, `COBIL00C`, `CBACT04C`, `CBEXPORT`, `CBIMPORT`이며, 온라인 프로그램으로는 `COTRN00C`(거래 목록), `COTRN01C`(거래 조회), `COTRN02C`(거래 추가), `CORPT00C`(보고서)가 있다.
- **트랜잭션 ID 또는 EXEC PGM**: 없음.

---

## Java/현대화 노트

### 1. DTO 변환 매핑

```java
public class TransactionRecord {
    private String tranId;           // TRAN-ID        PIC X(16)
    private String tranTypeCd;       // TRAN-TYPE-CD   PIC X(02)
    private int    tranCatCd;        // TRAN-CAT-CD    PIC 9(04)
    private String tranSource;       // TRAN-SOURCE    PIC X(10)
    private String tranDesc;         // TRAN-DESC      PIC X(100)
    private BigDecimal tranAmt;      // TRAN-AMT       PIC S9(09)V99 -> scale=2
    private long   tranMerchantId;   // TRAN-MERCHANT-ID PIC 9(09)
    private String tranMerchantName; // TRAN-MERCHANT-NAME PIC X(50)
    private String tranMerchantCity; // TRAN-MERCHANT-CITY PIC X(50)
    private String tranMerchantZip;  // TRAN-MERCHANT-ZIP PIC X(10)
    private String tranCardNum;      // TRAN-CARD-NUM  PIC X(16)
    private String tranOrigTs;       // TRAN-ORIG-TS   PIC X(26)
    private String tranProcTs;       // TRAN-PROC-TS   PIC X(26)
    // FILLER X(20) 무시
}
```

### 2. CVTRA06Y (`DALYTRAN-RECORD`)와의 관계

`CVTRA05Y`(`TRAN-RECORD`)와 `CVTRA06Y`(`DALYTRAN-RECORD`)는 **필드 구조와 바이트 레이아웃이 완전히 동일하다**. 유일한 차이는 01레벨 그룹명과 05레벨 필드 접두사이다:

| CVTRA05Y (TRAN-RECORD) | CVTRA06Y (DALYTRAN-RECORD) | 용도 |
|---|---|---|
| `TRAN-RECORD` | `DALYTRAN-RECORD` | 01레벨 그룹명 |
| `TRAN-ID` | `DALYTRAN-ID` | 거래 ID |
| `TRAN-AMT` | `DALYTRAN-AMT` | 거래 금액 |
| ... | ... | (이하 동일 패턴) |

`TRAN-RECORD`는 온라인 CICS 프로그램과 배치가 공통으로 읽고 쓰는 **마스터 TRANSACT KSDS** 레코드이고, `DALYTRAN-RECORD`는 **일일 배치 처리용 임시/스테이징 파일**(DALYTRAN)의 레코드이다. Java로 현대화할 때 두 레코드를 단일 `TransactionRecord` 클래스로 통합하고 출처(source)를 enum 필드로 구분하는 것이 권장된다.

### 3. 고정 길이 문자열 처리

COBOL `PIC X(n)` 필드는 공백으로 우측 패딩된 고정 길이 문자열이다. EBCDIC에서 ASCII로 변환 후 Java에서는 반드시 `String.trim()` 또는 `stripTrailing()`을 적용해야 한다. 특히 `TRAN-ID`를 DB 기본 키로 사용할 경우, 공백 미제거 시 조회 불일치가 발생한다.

### 4. 타임스탬프 파싱

`TRAN-ORIG-TS`와 `TRAN-PROC-TS`의 26자 형식은 `YYYY-MM-DD HH:MM:SS.UUUUUU`(추측)로 추정된다. 실제 형식은 `COTRN02C.cbl`에서 타임스탬프를 생성하는 로직을 확인해야 정확하다. Java 변환 시 `DateTimeFormatter`를 사용하며, 마이크로초 정밀도가 필요하면 `LocalDateTime`보다 `Instant` 또는 `OffsetDateTime`이 적합하다.

### 5. EBCDIC vs ASCII

메인프레임 VSAM 파일은 EBCDIC 인코딩으로 저장된다. Java에서 직접 읽을 때 `Charset.forName("IBM037")` 또는 `"IBM1047"`로 디코딩해야 하며, `PIC X` 필드와 `PIC 9` DISPLAY 필드 모두 해당된다.

### 6. 레코드 고정 길이 I/O

VSAM KSDS 레코드는 항상 350바이트 고정 길이로 읽고 써야 한다. AWS Mainframe Modernization(m2) 환경으로 이전할 경우 AWS SDK for Java의 `BatchFileProcessor` 또는 EMR의 COBOL 마이그레이션 도구가 이 고정 길이 레코드를 자동으로 처리한다.

---

*버전: CardDemo_v1.0-15-g27d6c6f-68 / 2022-07-19 23:16:01 CDT*
