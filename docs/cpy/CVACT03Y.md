# CVACT03Y — 카드/계정/고객 교차참조(CARDXREF) 레코드 레이아웃

- **유형**: Copybook (레코드 레이아웃 정의 전용)
- **한 줄 요약**: 카드번호(16자리)를 기본 키로, 계좌 ID와 고객 ID를 연결하는 VSAM KSDS `CARDXREF` 파일의 50바이트 레코드 구조를 정의한다.

---

## 기능 설명

`CVACT03Y`는 CardDemo 시스템에서 **카드번호 ↔ 고객 ID ↔ 계좌 ID** 세 엔터티를 연결하는 교차참조(cross-reference) 레코드의 물리적 레이아웃만을 담은 순수 데이터 copybook이다. PROCEDURE DIVISION 코드 없이 `DATA DIVISION`에 `COPY CVACT03Y.` 한 줄로 인클루드하면 `CARD-XREF-RECORD`라는 01 레벨 그룹 항목이 프로그램 작업 영역(WORKING-STORAGE 또는 FD 재정의 버퍼)에 펼쳐진다.

이 레코드가 저장되는 VSAM 파일은 `CARDXREF`(DD명 `XREFFILE`)이며, KSDS(Key-Sequenced Data Set) 구조로 다음 두 가지 키를 가진다.

- **기본 키(Primary Key)**: `XREF-CARD-NUM` (카드번호 16자리) — 카드번호로 직접 조회할 때 사용
- **보조 키(Alternate Key)**: `XREF-ACCT-ID` (계좌 ID 11자리) — CBACT04C에서 `ALTERNATE RECORD KEY IS FD-XREF-ACCT-ID`로 선언되어, 계좌 기준 역방향 조회에 사용

파일 레코드 길이는 50바이트로 고정(`RECLN 50`).

Java에서의 역할에 대응하면, 이 copybook은 `@Entity` 또는 단순 `record` DTO에 해당하며, `CARDXREF` VSAM 파일은 `card_number`를 PK로 하고 `acct_id`를 UK/인덱스로 갖는 관계형 테이블 또는 JPA `@Table`에 해당한다.

---

## 필드 레이아웃

소스 파일: `/Users/dongryunjeong/Documents/development/aws-mainframe-modernization-carddemo/app/cpy/CVACT03Y.cpy`

```
01 CARD-XREF-RECORD.                          총 50바이트
    05  XREF-CARD-NUM     PIC X(16)           오프셋  0, 길이 16
    05  XREF-CUST-ID      PIC 9(09)           오프셋 16, 길이  9
    05  XREF-ACCT-ID      PIC 9(11)           오프셋 25, 길이 11
    05  FILLER            PIC X(14)           오프셋 36, 길이 14  (합계 50)
```

| 레벨 | 필드명 | PIC / USAGE | 바이트 | 의미 및 Java 매핑 |
|------|--------|-------------|--------|------------------|
| 01 | `CARD-XREF-RECORD` | GROUP | 50 | 레코드 전체. Java `CardXrefRecord` 클래스/레코드 |
| 05 | `XREF-CARD-NUM` | `PIC X(16)` / DISPLAY | 16 | 카드번호. KSDS 기본 키. 영문자·숫자 혼용 가능하나 실제로는 숫자 16자리. Java `String` (고정 16자, 좌정렬·우공백 없음) |
| 05 | `XREF-CUST-ID` | `PIC 9(09)` / DISPLAY | 9 | 고객 ID. 숫자 9자리, DISPLAY 형식(EBCDIC 존드 수치). Java `long` 또는 `String` — DB 매핑 시 `BIGINT`(최대 999,999,999). 부호 없음, COMP 아님 |
| 05 | `XREF-ACCT-ID` | `PIC 9(11)` / DISPLAY | 11 | 계좌 ID. 숫자 11자리, DISPLAY 형식. 보조 키(Alternate Key)로도 활용됨(CBACT04C 38행). Java `long` 또는 `String` — `BIGINT`(최대 99,999,999,999) |
| 05 | `FILLER` | `PIC X(14)` | 14 | 예약 공간. 현재 사용되지 않음. Java 변환 시 무시하거나 `byte[] reserved` 로 보관 |

**주의 사항**

- `XREF-CUST-ID`, `XREF-ACCT-ID`는 `PIC 9(n)`이지만 **COMP / COMP-3 지정 없음** → DISPLAY(존드 10진수, EBCDIC 인코딩). 메인프레임에서 읽어 올 때 ASCII 변환이 필요하며, 각 자리는 1바이트를 차지한다.
- 88-level 조건명 없음. 레코드 존재 여부 판단은 호출 프로그램에서 FILE STATUS(`'00'`/`'23'` 등)로 처리한다(예: COCRDSLC.cbl 151행 `DID-NOT-FIND-ACCT-IN-CARDXREF`).
- REDEFINES, OCCURS 없음 — 단순 선형 레이아웃.
- FILLER 14바이트는 미래 확장 또는 레코드 정렬용 패딩으로 추정 (추측).

---

## 의존성

- **COPY (중첩 카피북)**: 없음. `CVACT03Y`는 다른 copybook을 내부에서 COPY하지 않는다.

- **호출 프로그램 (CALL/XCTL/LINK)**: 없음. Copybook이므로 실행 흐름을 포함하지 않는다. 이 레이아웃을 사용하는 프로그램 목록은 아래 참조.

  | 프로그램 | 유형 | 접근 방식 |
  |----------|------|-----------|
  | `CBACT03C` (cbl/45행) | 배치 | SEQUENTIAL READ, 기본 키 |
  | `CBACT04C` (cbl/38행) | 배치 | RANDOM READ, 기본 키 + 보조 키(ACCT-ID) |
  | `CBTRN01C` (cbl/40행) | 배치 | RANDOM READ, 기본 키 |
  | `CBTRN02C` (cbl/40행) | 배치 | RANDOM READ, 기본 키 |
  | `CBTRN03C` (cbl) | 배치 | RANDOM READ, 기본 키 |
  | `COBIL00C` (cbl) | 온라인(CICS) | EXEC CICS READ |
  | `COACTUPC` (cbl/643행) | 온라인(CICS) | `EXEC CICS READ DATASET('CXACAIX')` — 보조 경로(AIX)로 ACCT-ID 기준 조회 |
  | `COACTVWC` (cbl) | 온라인(CICS) | `EXEC CICS READ DATASET('CXACAIX')` — 동일 보조 경로 |
  | `COTRN02C` (cbl) | 온라인(CICS) | EXEC CICS READ |
  | `COCRDSLC` (cbl) | 온라인(CICS) | `*COPY CVACT03Y.` (주석 처리됨, 필드 직접 참조) |
  | `COCRDUPC` (cbl) | 온라인(CICS) | `*COPY CVACT03Y.` (주석 처리됨, 필드 직접 참조) |
  | `COPAUA0C` / `COPAUS0C` (authorization 모듈) | 온라인(CICS+MQ) | EXEC CICS READ |
  | `CBEXPORT` / `CBIMPORT` (cbl) | 배치 유틸리티 | SEQUENTIAL I/O |
  | `CVEXPORT.cpy` | Copybook | `CARD-XREF-RECORD` 필드 재사용 |

- **데이터셋/파일/DB 테이블**:
  - VSAM KSDS `CARDXREF` (DD명: `XREFFILE`, 레코드 길이 50)
    - 기본 키: `XREF-CARD-NUM` (PIC X(16))
    - 보조 키(AIX): `XREF-ACCT-ID` (PIC 9(11)) — AIX 데이터셋명 `CXACAIX` (`COACTUPC` 582행, `COACTVWC` 192행)
  - 관계형 DB 대응: `account_card_xref` 또는 `card_account_mapping` 테이블 (card_number PK, acct_id UNIQUE INDEX)

- **트랜잭션 ID 또는 EXEC PGM**: 없음. Copybook은 트랜잭션/프로그램 이름을 가지지 않는다.

---

## Java/현대화 노트

### 1. Java 레코드 변환

```java
/**
 * CVACT03Y — CARD-XREF-RECORD (50 bytes, VSAM KSDS CARDXREF)
 * 기본 키: cardNum (16자), 보조 키: acctId (11자리)
 */
public record CardXrefRecord(
    String  cardNum,   // XREF-CARD-NUM  PIC X(16)  — 기본 키
    long    custId,    // XREF-CUST-ID   PIC 9(09)  — 고객 ID
    long    acctId,    // XREF-ACCT-ID   PIC 9(11)  — 계좌 ID (보조 키)
    byte[]  filler     // FILLER PIC X(14) — 예약 패딩, 무시 가능
) {
    // COBOL DISPLAY 숫자 → long 변환 예시
    public static CardXrefRecord fromBytes(byte[] raw) {
        // EBCDIC → ASCII 변환 후 파싱 필요
        String cardNum = new String(raw, 0, 16, StandardCharsets.ISO_8859_1).trim();
        long custId  = Long.parseLong(new String(raw, 16, 9, StandardCharsets.ISO_8859_1).trim());
        long acctId  = Long.parseLong(new String(raw, 25, 11, StandardCharsets.ISO_8859_1).trim());
        return new CardXrefRecord(cardNum, custId, acctId, Arrays.copyOfRange(raw, 36, 50));
    }
}
```

### 2. JPA 엔터티 매핑

```java
@Entity
@Table(name = "card_xref",
       uniqueConstraints = @UniqueConstraint(columnNames = "acct_id"))
public class CardXref {
    @Id
    @Column(name = "card_num", length = 16, nullable = false)
    private String cardNum;          // XREF-CARD-NUM PIC X(16)

    @Column(name = "cust_id", nullable = false)
    private Long custId;             // XREF-CUST-ID  PIC 9(09)

    @Column(name = "acct_id", nullable = false)
    private Long acctId;             // XREF-ACCT-ID  PIC 9(11) — AIX 보조 키
}
```

### 3. 데이터 타입 주의점

| 항목 | COBOL | Java | 주의 |
|------|-------|------|------|
| `XREF-CARD-NUM` | `PIC X(16)` DISPLAY | `String` (16자) | EBCDIC→UTF-8 변환 시 16바이트 그대로 유지. 선행/후행 공백 trim 여부 확인 필요 |
| `XREF-CUST-ID` | `PIC 9(09)` DISPLAY | `long` / `int` | COMP 아님 → 9바이트 존드 10진수. 최대값 999,999,999 → `int` 범위 초과 불가 (`Integer.MAX_VALUE` = 2,147,483,647) — 그러나 안전을 위해 `long` 권장 |
| `XREF-ACCT-ID` | `PIC 9(11)` DISPLAY | `long` | 최대 99,999,999,999 → `int` 범위 초과 → 반드시 `long` 또는 `BigInteger` 사용 |
| `FILLER` | `PIC X(14)` | `byte[]` 또는 생략 | 현재 미사용. DB 마이그레이션 시 컬럼 미생성 권장 |

### 4. VSAM AIX 보조 경로 처리

COACTUPC·COACTVWC는 VSAM KSDS의 기본 경로가 아니라 **보조 인덱스 경로(AIX)** `CXACAIX`를 통해 `XREF-ACCT-ID` 기준으로 레코드를 조회한다 (`EXEC CICS READ DATASET('CXACAIX') RIDFLD(acct-id) ...`). Java 마이그레이션 시 이 패턴은 JPA의 `acct_id`에 걸린 UNIQUE INDEX 또는 `findByAcctId()` Spring Data 메서드로 대체하면 된다. 단, VSAM AIX는 중복 보조 키를 허용할 수 있으나(WITH DUPLICATES 옵션), 이 시스템에서 계좌 1개에 카드 1개를 전제하므로 UNIQUE 제약이 적절하다 (추측 — WITH DUPLICATES 여부는 DEFINE CLUSTER JCL 미확인).

### 5. FILLER 패딩의 의미

14바이트 FILLER는 추후 필드 추가를 위한 레이아웃 예약이거나 레코드를 특정 블록 크기로 정렬하기 위한 패딩으로 보인다. 현재 어떤 프로그램도 이 영역에 값을 쓰거나 읽지 않으므로, 관계형 DB 마이그레이션 시 컬럼으로 생성하지 않아도 된다.

---

*소스 버전: CardDemo_v1.0-15-g27d6c6f-68 / 2022-07-19 (CVACT03Y.cpy 10행 주석)*
