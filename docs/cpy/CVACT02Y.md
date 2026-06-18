# CVACT02Y — 카드 마스터 레코드 레이아웃

- **유형**: Copybook
- **한 줄 요약**: VSAM KSDS 파일 `CARDDATA`의 고정 길이 150바이트 카드 마스터 레코드(`CARD-RECORD`)를 정의하는 공유 스키마 copybook으로, 카드 번호·연결 계정·CVV·엠보싱 이름·유효기간·활성 상태를 담는다.

---

## 기능 설명

`CVACT02Y`는 신용카드 한 장의 물리적 정보를 VSAM KSDS 레코드 형태로 표현하는 데이터 정의 copybook이다. 실행 코드는 전혀 없고, `01 CARD-RECORD` 아래에 7개의 하위 필드와 59바이트 FILLER로 구성된 150바이트 레이아웃만을 선언한다.

이 copybook은 온라인(CICS) 프로그램과 배치 프로그램이 **동일한 구조체**를 공유하는 DTO(Data Transfer Object) 역할을 한다. 카드 번호(`CARD-NUM`)가 KSDS의 기본 키(primary key)이며, `CARD-ACCT-ID`를 통해 계정 마스터(`CVACT01Y`의 `ACCOUNT-RECORD`)와 논리적으로 연결된다.

소스 버전 주석(14행): `CardDemo_v1.0-15-g27d6c6f-68  2022-07-19 23:16:00 CDT`

---

## 필드 레이아웃

레코드 총 길이: **150바이트** (RECLN 150, 소스 1행 주석 근거)

| 레벨 | 필드명 | PIC / USAGE | 바이트 | 의미 및 Java 대응 |
|------|--------|------------|--------|-------------------|
| 05 | `CARD-NUM` | `PIC X(16)` | 16 | 16자리 카드 번호(ISO/IEC 7812 PAN). DISPLAY 문자열. Java: `String` (16자 고정). KSDS 기본 키 — 프로그램에서 RIDFLD/KEY로 사용됨 |
| 05 | `CARD-ACCT-ID` | `PIC 9(11)` | 11 | 연결된 계정 ID. 순수 숫자 DISPLAY(존 10진). Java: `long` 또는 `String(11)`. `CVACT01Y`의 `ACCT-ID PIC 9(11)`과 동일 도메인 |
| 05 | `CARD-CVV-CD` | `PIC 9(03)` | 3 | 3자리 CVV 코드. 순수 숫자 DISPLAY. Java: `short` 또는 보안상 `String`. 실운영에서는 평문 저장 금지 주의 |
| 05 | `CARD-EMBOSSED-NAME` | `PIC X(50)` | 50 | 카드 표면 엠보싱 이름. DISPLAY 문자열 50바이트 고정 패딩. Java: `String` (trim 필수, EBCDIC→ASCII 변환 주의) |
| 05 | `CARD-EXPIRAION-DATE` | `PIC X(10)` | 10 | 유효기간. 형식 불명확(추측: `YYYY-MM-DD` 또는 `MM/YYYY`). Java: `String` → 파싱 후 `YearMonth` 권장. 필드명 오탈자(`EXPIRAION`←`EXPIRATION`) 그대로 유지해야 컴파일 오류 없음 |
| 05 | `CARD-ACTIVE-STATUS` | `PIC X(01)` | 1 | 카드 활성 상태 플래그. 값 도메인 불명확(추측: `'Y'`/`'N'` 또는 `'A'`/`'I'`). 88-level 조건명 없음 — 프로그램마다 리터럴 비교. Java: `enum CardStatus { ACTIVE, INACTIVE }` 또는 `boolean` |
| 05 | `FILLER` | `PIC X(59)` | 59 | 예약 공간. 미래 확장 또는 레코드 길이 맞춤용. Java 매핑 시 무시하거나 `byte[] reserved` |

**합계 검증**: 16 + 11 + 3 + 50 + 10 + 1 + 59 = **150바이트** (일치)

---

## 의존성

- **COPY (중첩 카피북)**: 없음 — `CVACT02Y` 자체는 다른 copybook을 COPY하지 않는다.

- **호출 프로그램 (CALL/XCTL/LINK)**: 없음 — copybook은 데이터 정의만 포함하며 실행 코드가 없다.

- **데이터셋/파일/DB 테이블**: VSAM KSDS `CARDDATA` (레코드 길이 150바이트, 기본 키 `CARD-NUM PIC X(16)`). 이 copybook을 COPY하는 프로그램 목록:

  | 프로그램 | 유형 | 용도 |
  |---------|------|------|
  | `CBACT02C` | 배치 | 카드 마스터 VSAM I/O (조회/갱신) |
  | `CBTRN01C` | 배치 | 거래 처리 시 카드 유효성 조회 |
  | `CBEXPORT` | 배치 | VSAM → 순차 파일 익스포트 |
  | `CBIMPORT` | 배치 | 순차 파일 → VSAM 임포트 |
  | `COCRDLIC` | 온라인(CICS) | 카드 목록 조회 화면 |
  | `COCRDSLC` | 온라인(CICS) | 카드 선택/상세 조회 |
  | `COCRDUPC` | 온라인(CICS) | 카드 정보 수정 화면 |
  | `COACTVWC` | 온라인(CICS) | 계정+카드 통합 조회 화면 |
  | `COPAUS0C` | 온라인(CICS/IMS) | 승인 처리 시 카드 조회 (MQ 연동) |
  | `COTRTLIC` | 온라인(CICS/DB2) | 거래 유형 연동 카드 조회 |

- **트랜잭션 ID 또는 EXEC PGM**: 없음 — copybook 자체에 트랜잭션 ID나 실행 PGM이 없다.

---

## Java/현대화 노트

### 1. Java 클래스 매핑

```java
public class CardRecord {
    // PIC X(16) — KSDS 기본 키, 고정 16자 패딩 유지 필요
    private String cardNum;          // 예: "4111111111111111"

    // PIC 9(11) — 순수 숫자 DISPLAY (존 10진, 부호 없음)
    private long cardAcctId;         // 또는 String으로 원본 포맷 보존

    // PIC 9(03) — 3자리 CVV, 보안 처리 필수
    private short cardCvvCd;         // 실운영: 암호화 후 String 저장 권장

    // PIC X(50) — EBCDIC 50바이트, 우측 공백 패딩
    private String cardEmbossedName; // trim() 후 사용

    // PIC X(10) — 유효기간 문자열, 형식 파싱 후 타입 변환
    private String cardExpirationDate; // → YearMonth 변환 권장

    // PIC X(01) — 활성 상태 플래그
    private String cardActiveStatus; // → enum 변환 권장

    // PIC X(59) — FILLER (무시)
    // private byte[] reserved = new byte[59]; // 바이너리 변환 시만 필요
}
```

### 2. 핵심 현대화 주의사항

**고정 길이 레코드 처리**: VSAM 레코드는 정확히 150바이트 고정이다. Java에서 VSAM을 직접 읽을 때(예: AWS Mainframe Modernization의 데이터 마이그레이션 후 파일 기반 접근) `substring(offset, offset+length)` 방식으로 각 필드를 추출해야 한다.

```java
// 150바이트 레코드 파싱 예시
String raw = new String(recordBytes, "IBM037"); // EBCDIC → Java String
String cardNum          = raw.substring(0, 16).trim();
long   cardAcctId       = Long.parseLong(raw.substring(16, 27).trim());
short  cardCvvCd        = Short.parseShort(raw.substring(27, 30).trim());
String embossedName     = raw.substring(30, 80).trim();
String expirationDate   = raw.substring(80, 90).trim();
String activeStatus     = raw.substring(90, 91);
// bytes 91–149: FILLER 무시
```

**EBCDIC vs ASCII**: COBOL 프로그램은 EBCDIC 인코딩으로 동작한다. `PIC X` 필드의 공백은 EBCDIC `0x40`이며, Java의 공백(`0x20`)과 다르다. 데이터 마이그레이션 시 `Charset.forName("IBM037")`(EBCDIC 코드페이지 037)로 변환해야 한다.

**`CARD-EXPIRAION-DATE` 오탈자**: 원본 COBOL 소스(9행)에 `EXPIRAION`(EXPIRATION의 오탈자)이 있다. Java 클래스 필드명을 `cardExpirationDate`로 정정해도 되나, 혹시 COBOL에서 이 필드를 COPY-REPLACING으로 치환하는 코드가 있다면 오탈자 그대로 맞춰야 한다. 현재 프로젝트에서는 COPY-REPLACING 사용이 확인되지 않아 정정 가능(추측).

**`CARD-ACTIVE-STATUS` 도메인 미정의**: 88-level 조건명이 없어 허용 값이 copybook만으로는 알 수 없다. `COCRDUPC.cbl`·`COCRDSLC.cbl` 등 사용 프로그램의 조건식을 직접 확인해 실제 값 도메인(`'Y'`/`'N'` 또는 기타)을 파악한 뒤 Java `enum`으로 명시화할 것을 권장한다.

**CVV 보안**: `CARD-CVV-CD PIC 9(03)`은 카드 CVV를 평문으로 저장한다. Java 현대화 시 PCI DSS 요건에 따라 이 필드를 데이터베이스에 저장하지 않거나 암호화 처리해야 한다.

**관계 모델 변환**:
```
COBOL (VSAM 파일 분리)          Java/RDB
CARDDATA (CARD-RECORD)    →   card 테이블 (PK: card_num VARCHAR(16))
  └─ CARD-ACCT-ID 9(11)   →   FK → account 테이블 (CVACT01Y)
  └─ CARD-NUM → CARDXREF  →   card_xref 테이블 (CVACT03Y)로 고객 연결
```

**FILLER 59바이트**: 현재 미사용이지만 향후 필드 추가 공간으로 예약되어 있다. DB 스키마 설계 시 이 공간을 무시해도 되나, 레거시 데이터 마이그레이션 시 원본 레코드 길이(150바이트) 보존이 필요하다면 `reserved BLOB` 컬럼으로 저장해 두는 것이 안전하다.
