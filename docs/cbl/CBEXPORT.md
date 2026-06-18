# CBEXPORT — 고객 데이터 추출 배치 (지점 마이그레이션용 Export)

- **유형**: 배치 COBOL (CICS를 거치지 않고 VSAM 파일을 직접 OPEN; `PROGRAM-ID. CBEXPORT`, L2 / 헤더 주석 `Type: BATCH COBOL Program`, L7)
- **한 줄 요약**: CardDemo의 정규화된 5개 마스터 파일(고객·계정·교차참조·거래·카드)을 순차 스캔하여, 각 레코드를 레코드 타입 식별자가 붙은 500바이트 고정 길이 "멀티 레코드 export 파일" 하나로 합쳐 내보내는 데이터 마이그레이션 배치 (L8-11, L27-31).

## 기능 설명

지점 마이그레이션(Branch Migration)을 위해 5종의 입력 파일을 각각 끝까지 읽으면서, 한 레코드를 읽을 때마다 공통 500바이트 레이아웃의 export 레코드로 변환해 단일 출력 파일에 기록한다. 출력 레코드의 첫 바이트(`EXPORT-REC-TYPE`)에 종류를 표시한다:

| 코드 | 의미 | 단락 |
|------|------|------|
| `C` | Customer (고객) | `2200-CREATE-CUSTOMER-EXP-REC` (L274) |
| `A` | Account (계정) | `3200-CREATE-ACCOUNT-EXP-REC` (L343) |
| `X` | Cross-Reference (카드↔계정↔고객 교차참조) | `4200-CREATE-XREF-EXPORT-RECORD` (L407) |
| `T` | Transaction (거래) | `5200-CREATE-TRAN-EXP-REC` (L462) |
| `D` | Card (카드, "carD") | `5700-CREATE-CARD-EXPORT-RECORD` (L527) |

모든 export 레코드 앞부분에는 공통 헤더 필드가 들어간다: 타입(1B), 추출 타임스탬프(26B), 시퀀스 번호, 지점 ID, 지역 코드. 지점 ID·지역 코드는 **하드코딩**되어 있다 — 모든 레코드에 `'0001'` / `'NORTH'`가 들어간다 (L278-279 등 5곳 반복). 즉 이 배치는 사실상 "1번 지점, NORTH 지역" 한 곳의 데이터를 통째로 추출하는 용도다.

처리 통계(고객/계정/xref/거래/카드별 건수 + 총 건수)를 누적해 종료 시 `DISPLAY`로 출력한다 (L564-573).

자바 관점 한 줄 비유: "여러 테이블(Entity)을 각각 `findAll()` 한 뒤, 공통 부모 클래스를 상속한 DTO로 매핑해 하나의 export 파일로 직렬화하는 ETL 잡". 단, 자바라면 보통 다형성(상속/sealed type)으로 풀 것을 COBOL은 `REDEFINES`(같은 메모리 영역의 여러 해석) + 1바이트 타입 태그로 표현한다.

## 입력 / 출력

- **입력** (모두 VSAM KSDS, `ORGANIZATION INDEXED ACCESS SEQUENTIAL` = 키 오름차순 순차 읽기, L35-63):
  - `CUSTFILE` (고객, 키 `CUST-ID`) — 레이아웃 `CVCUS01Y`
  - `ACCTFILE` (계정, 키 `ACCT-ID`) — 레이아웃 `CVACT01Y`
  - `XREFFILE` (카드↔계정↔고객 교차참조, 키 `XREF-CARD-NUM`) — 레이아웃 `CVACT03Y`
  - `TRANSACT` (거래, 키 `TRAN-ID`) — 레이아웃 `CVTRA05Y`
  - `CARDFILE` (카드, 키 `CARD-NUM`) — 레이아웃 `CVACT02Y`
- **출력**:
  - `EXPFILE` — export 파일. **SELECT 절에는 `ORGANIZATION INDEXED ... RECORD KEY IS EXPORT-SEQUENCE-NUM`(L65-68)으로 KSDS로 선언**되어 있으나, **FD에는 `RECORDING MODE IS F` / `RECORD CONTAINS 500 CHARACTERS`(L89-91)**, 레코드는 단일 `PIC X(500)`(L92). (주의: SELECT의 INDEXED 선언과 FD의 순차 고정길이 선언이 상충함 — Java/현대화 노트의 "주요 결함" 참조.)

## 의존성

- **COPY (카피북)**:
  - `CVCUS01Y` — 고객 레코드 (FD CUSTOMER-INPUT, L75)
  - `CVACT01Y` — 계정 레코드 `ACCOUNT-RECORD` (FD ACCOUNT-INPUT, L78)
  - `CVACT03Y` — 카드 교차참조 `CARD-XREF-RECORD` (FD XREF-INPUT, L81)
  - `CVTRA05Y` — 거래 레코드 `TRAN-RECORD` (FD TRANSACTION-INPUT, L84)
  - `CVACT02Y` — 카드 레코드 `CARD-RECORD` (FD CARD-INPUT, L87)
  - `CVEXPORT` — **출력 export 레코드 멀티 레이아웃** `EXPORT-RECORD` (WORKING-STORAGE, L96). 이 copybook이 이 프로그램의 핵심 산출물 구조.
- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `CALL 'CEE3ABD'` — LE(Language Environment) 강제 abend 서비스. 오류 시 `9999-ABEND-PROGRAM`에서만 호출 (L579). 비즈니스 로직 호출은 없음.
- **데이터셋/파일/DB 테이블**: 위 입력 5개 VSAM + 출력 1개. DB2/SQL·CICS·IMS 일체 없음 (순수 파일 배치).
- **트랜잭션 ID 또는 EXEC PGM**: 트랜잭션 ID 없음(온라인 아님). JCL에서 `EXEC PGM=CBEXPORT`로 기동될 것으로 추정 — DD명 `CUSTFILE/ACCTFILE/XREFFILE/TRANSACT/CARDFILE/EXPFILE`가 ASSIGN 명과 매칭됨 (추측: 전용 export JCL/job 존재. 리포지토리에 export JCL이 추가됐다는 최근 커밋 메시지와 정황상 일치하나 본 소스만으로는 미확인).

## 핵심 로직 흐름

메인 단락 `0000-MAIN-PROCESSING`은 자바의 `main()`에 해당하며, 6개 단계를 순서대로 PERFORM 후 `GOBACK`한다 (L149-158):

```
0000-MAIN-PROCESSING:
    PERFORM 1000-INITIALIZE        -- 타임스탬프 생성 + 6개 파일 OPEN
    PERFORM 2000-EXPORT-CUSTOMERS  -- 고객  → 'C' 레코드
    PERFORM 3000-EXPORT-ACCOUNTS   -- 계정  → 'A' 레코드
    PERFORM 4000-EXPORT-XREFS      -- 교차참조 → 'X' 레코드
    PERFORM 5000-EXPORT-TRANSACTIONS -- 거래 → 'T' 레코드
    PERFORM 5500-EXPORT-CARDS      -- 카드  → 'D' 레코드
    PERFORM 6000-FINALIZE          -- 6개 파일 CLOSE + 통계 출력
    GOBACK.
```

1. **1000-INITIALIZE** (L161): 시작 메시지 출력 → `1050-GENERATE-TIMESTAMP` → `1100-OPEN-FILES`.
   - **1050-GENERATE-TIMESTAMP** (L172): `ACCEPT ... FROM DATE YYYYMMDD` / `FROM TIME`으로 현재 일시 취득 후, `STRING`으로 `YYYY-MM-DD`(L179), `HH:MM:SS`(L185), 그리고 26자 타임스탬프 `YYYY-MM-DD HH:MM:SS.00`(L191)을 조립. 이 타임스탬프가 모든 export 레코드에 동일하게 박힌다(배치 1회 실행 = 단일 추출 시각).
   - **1100-OPEN-FILES** (L198): 입력 5개 `OPEN INPUT`, 출력 1개 `OPEN OUTPUT`. 각 OPEN 직후 FILE STATUS 88레벨(`WS-...-OK` = '00')을 검사, 실패 시 즉시 `9999-ABEND-PROGRAM`.

2. **2000~5500 각 export 단락**: 모두 동일한 "read-ahead 루프" 골격 — 자바의 `while(reader.hasNext())` 패턴과 동치.

   ```
   2000-EXPORT-CUSTOMERS:
       PERFORM 2100-READ-CUSTOMER-RECORD          -- priming read (첫 건 선독)
       PERFORM UNTIL WS-CUSTOMER-EOF              -- EOF(status '10') 까지
           PERFORM 2200-CREATE-CUSTOMER-EXP-REC   -- 변환 + WRITE
           PERFORM 2100-READ-CUSTOMER-RECORD      -- 다음 건
       END-PERFORM
   ```

   - **READ 단락** (예 L258): `READ` 후 status가 '00'(OK)도 '10'(EOF)도 아니면 진짜 오류로 보고 abend. EOF는 88레벨 `WS-...-EOF`('10')로 루프 종료 조건이 됨.
   - **CREATE 단락** (예 L269): `INITIALIZE EXPORT-RECORD`로 460바이트 데이터부를 공백/0으로 초기화 → 공통 헤더 세팅(타입, 타임스탬프, `ADD 1 TO WS-SEQUENCE-COUNTER` 후 시퀀스 부여, 지점/지역 하드코딩) → 입력 레코드의 각 필드를 export 레이아웃의 대응 필드로 `MOVE` → `WRITE EXPORT-OUTPUT-RECORD FROM EXPORT-RECORD` → 쓰기 status 검사 → 해당 카운터 + 총 카운터 `ADD 1`.
   - **OCCURS 필드 매핑 예** (고객, L286-288, L292-293): 고객 주소 3줄은 `EXP-CUST-ADDR-LINE(1..3)`, 전화 2개는 `EXP-CUST-PHONE-NUM(1..2)`로 1-based 첨자 직접 지정. 자바라면 `List<String>` 또는 배열.

3. **6000-FINALIZE** (L554): 6개 파일 `CLOSE` 후 종목별 건수와 총 건수 `DISPLAY`. (주의: CLOSE는 status 검사 없음.)

4. **9999-ABEND-PROGRAM** (L576): `DISPLAY 'ABENDING'` 후 `CALL 'CEE3ABD'`로 강제 abend. 어떤 파일 OPEN/READ/WRITE라도 실패하면 즉시 이리로 와서 잡 전체를 비정상 종료시킨다 → "부분 export"를 남기지 않는 all-or-nothing 정책.

### 출력 레코드 구조 (`CVEXPORT`) 와 데이터 표현

500바이트 = 공통 헤더 40바이트 + 데이터부 460바이트. 데이터부 `EXPORT-RECORD-DATA PIC X(460)`(copybook L19)를 5개 레이아웃이 `REDEFINES`로 덮어쓴다 — 같은 460바이트를 레코드 타입에 따라 다르게 해석. (자바엔 직접 대응 없음 → "주요 결함/노트" 참조.)

| 공통 헤더 필드 | PIC | 비고 |
|---|---|---|
| `EXPORT-REC-TYPE` | `X(1)` | C/A/X/T/D |
| `EXPORT-TIMESTAMP` | `X(26)` | `YYYY-MM-DD HH:MM:SS.00`. `EXPORT-TIMESTAMP-R`로 날짜/구분자/시간 재분해 가능(copybook L12-15) |
| `EXPORT-SEQUENCE-NUM` | **`9(9) COMP`** | 4바이트 **이진(binary)** 정수. 출력 KEY로도 선언됨(L68) |
| `EXPORT-BRANCH-ID` | `X(4)` | 하드코딩 `'0001'` |
| `EXPORT-REGION-CODE` | `X(5)` | 하드코딩 `'NORTH'` |

데이터부에 **DISPLAY가 아닌 압축/이진 숫자 필드가 혼재**한다(파일 크기 절감 목적, copybook L6 주석). 마이그레이션 시 가장 위험한 부분이므로 명시한다:

| 필드 | PIC | 저장형 | 바이트 | Java 매핑 / 주의 |
|---|---|---|---|---|
| `EXP-CUST-ID` | `9(9) COMP` | 이진 | 4 | `int`. (참고: 입력 `CUST-ID`는 보통 zoned `9(9)` DISPLAY → COMP로 MOVE 시 표현형 변환) |
| `EXP-CUST-FICO-CREDIT-SCORE` | `9(3) COMP-3` | 팩 십진 | 2 | `int`(0~999) |
| `EXP-ACCT-CURR-BAL` | `S9(10)V99 COMP-3` | 팩 십진(부호) | 7 | `BigDecimal` scale=2 |
| `EXP-ACCT-CREDIT-LIMIT` | `S9(10)V99` | zoned DISPLAY | 12 | `BigDecimal` scale=2 (이건 텍스트형) |
| `EXP-ACCT-CASH-CREDIT-LIMIT` | `S9(10)V99 COMP-3` | 팩 십진 | 7 | `BigDecimal` scale=2 |
| `EXP-ACCT-CURR-CYC-CREDIT` | `S9(10)V99` | zoned DISPLAY | 12 | `BigDecimal` scale=2 |
| `EXP-ACCT-CURR-CYC-DEBIT` | `S9(10)V99 COMP` | **이진(부호)** | 8 | `BigDecimal` — 이진+묵시소수점 조합이라 디코딩 까다로움 |
| `EXP-TRAN-CAT-CD` | `9(4)` | zoned DISPLAY | 4 | `int` |
| `EXP-TRAN-AMT` | `S9(9)V99 COMP-3` | 팩 십진 | 6 | `BigDecimal` scale=2 |
| `EXP-TRAN-MERCHANT-ID` | `9(9) COMP` | 이진 | 4 | `int`/`long` |
| `EXP-XREF-CUST-ID` | `9(9)` | zoned DISPLAY | 9 | `int` |
| `EXP-XREF-ACCT-ID` | `9(11) COMP` | 이진 | 8 | `long` |
| `EXP-CARD-ACCT-ID` | `9(11) COMP` | 이진 | 8 | `long` |
| `EXP-CARD-CVV-CD` | `9(3) COMP` | 이진 | 4 | `int` |

> 핵심: **같은 의미의 필드라도 export 레이아웃마다 저장형이 다르다.** 예) 계정 ID가 `EXP-ACCT-ID`는 `9(11)` DISPLAY(L48)인데 `EXP-XREF-ACCT-ID`/`EXP-CARD-ACCT-ID`는 `9(11) COMP` 이진(L87, L95). 통합 처리 시 레코드 타입별로 다른 디코딩 경로가 필요하다.

## Java/현대화 노트

- **REDEFINES 멀티 레코드 → 다형성으로 치환**: `EXPORT-RECORD`는 "1바이트 태그 + 같은 메모리의 5가지 해석"이다. Java에서는 공통 헤더(`ExportHeader`: type, timestamp, seq, branchId, regionCode)를 가진 추상/sealed 베이스에 `CustomerExport`, `AccountExport`, `XrefExport`, `TransactionExport`, `CardExport`를 두고, `recType`으로 분기(파싱)·다형 직렬화(쓰기)하는 구조가 자연스럽다. COBOL의 `REDEFINES`처럼 "같은 바이트를 동시에 두 뷰로" 보는 개념은 Java에 없으니, 읽을 때 태그 보고 한 가지 타입으로 역직렬화한다.

- **`INITIALIZE` 후 `MOVE`**: 각 CREATE 단락이 매번 460바이트 데이터부를 초기화하고 필요한 필드만 채운다. Java에선 매 반복마다 새 DTO 객체를 `new` 하는 것과 같다(이전 레코드 잔여값 오염 방지). DTO 불변 객체 + 빌더 권장.

- **read-ahead(priming read) 루프**: priming read + `PERFORM UNTIL EOF` + 루프 끝 read 패턴은 Java `Iterator`/`while(reader.hasNext()){ map(); write(); }` 또는 Spring Batch의 `ItemReader→ItemProcessor→ItemWriter`로 직역된다. 5개 입력은 5개의 독립 reader/step. **이 잡은 조인이 전혀 없다** — 각 파일을 그냥 통째로 덤프할 뿐이라 step 간 의존성이 없어 병렬화도 가능.

- **고정 길이 + 이진 필드 = 가장 큰 마이그레이션 함정**: 출력이 텍스트 CSV가 아니라 **EBCDIC 고정 길이 + COMP/COMP-3 이진**이다. 다운스트림 소비자가 메인프레임이 아니라면 (1) EBCDIC↔ASCII 변환, (2) COMP/COMP-3 디코딩(이진 정수, 팩 십진), (3) 묵시 소수점(`V99`) 스케일 복원을 정확히 구현해야 한다. 자바에서는 BigDecimal로 받되, 나눗셈이 없으므로 반올림 이슈는 없지만 **스케일(2자리) 고정**과 부호 처리에 주의. (개선 제안: 마이그레이션 타깃이 비메인프레임이면 출력을 CSV/JSON 등 자기서술적 포맷으로 바꾸는 편이 안전.)

- **`9(9) COMP` 시퀀스 = 자바 카운터**: `WS-SEQUENCE-COUNTER PIC 9(9)`를 1부터 증가시켜 전 레코드에 걸쳐 유일 시퀀스를 부여(타입 무관 전역 일련번호). Java `AtomicLong`/단순 카운터. 약 10억(9자리)까지 안전, `int`로 충분하나 경계 의식.

- **에러 처리 = fail-fast all-or-nothing**: 어떤 I/O 실패든 `CEE3ABD`로 잡 abort. Java에선 unchecked 예외를 던져 트랜잭션/스텝 전체를 롤백·실패시키는 것과 동치. **단, 출력 파일에 대한 트랜잭션 경계가 없어** abend 시 이미 쓴 레코드는 남는다(부분 파일). 현대화 시 출력 임시파일→성공 후 rename, 또는 Spring Batch 재시작 가능성(restartability) 고려 권장.

- **하드코딩된 branch/region**: `'0001'`/`'NORTH'`가 5곳에 반복. 마이그레이션 시 설정(파라미터/잡 인자)으로 외부화할 것. 현재 구조로는 "다른 지점 export"가 불가능하다(소스 수정 필요).

### 주요 결함/모순 (마이그레이션 전 반드시 정리)

1. **출력 파일 선언 불일치 (심각)**: SELECT는 `EXPFILE`을 `ORGANIZATION INDEXED ... RECORD KEY IS EXPORT-SEQUENCE-NUM`(KSDS, L65-68)로 선언하지만, FD는 `RECORDING MODE IS F`(순차 고정 길이, L89-91)다. 같은 파일을 KSDS와 순차로 동시에 선언한 모순 — 컴파일/런타임에서 어떻게 해석될지는 컴파일러·JCL DD 설정에 의존한다. (추측: 작성 의도는 "순차 고정 길이 export"이고 INDEXED/KEY 선언은 다른 입력 SELECT를 복붙하다 남은 잔재로 보임.) 더해 KSDS라면 키가 오름차순이어야 하는데 시퀀스는 단순 증가라 우연히 정렬되지만, **키 필드 `EXPORT-SEQUENCE-NUM`이 `COMP` 이진**(copybook L16)이라 일반적인 KSDS 키 취급과도 어긋난다. → Java/타깃 설계 시 "단순 순차 출력 파일"로 보는 것이 안전.

2. **이진 필드를 RECORDING MODE F 텍스트 레코드에 혼재**: 위 "데이터 표현" 표대로 COMP/COMP-3이 섞여 있어, 이 파일은 텍스트로 열면 깨진다. 다운스트림이 동일 copybook(`CVEXPORT`)으로 읽지 않으면 해석 불가.

3. **동일 의미 필드의 저장형 불일치**: 계정 ID가 레이아웃마다 DISPLAY/COMP로 다름(상기). 의도된 최적화인지 실수인지 불명확 — 통합 디코더에서 타입별 분기 필수.

4. **`EXP-CUST-SSN` 등 민감정보(SSN, CVV)를 평문/이진으로 export**: 마이그레이션 파일에 SSN(`EXP-CUST-SSN`), CVV(`EXP-CARD-CVV-CD`)가 마스킹 없이 포함된다. 컴플라이언스(PCI/PII) 관점에서 전송·저장 암호화 또는 마스킹 검토 필요.

5. **출력 레이아웃의 입력 copybook 동기화 위험**: `CVEXPORT`의 각 EXP-필드는 입력 copybook(`CVCUS01Y` 등) 필드를 손으로 복제한 것이다. 입력 레이아웃이 바뀌면 `CVEXPORT`와 CREATE 단락의 `MOVE`를 같이 고쳐야 함(자동 추적 없음). MOVE 시 길이/형 불일치가 있으면 잘림·정렬오류 가능 — 실제 매핑 검증 권장.

> 참고: 입력 copybook(`CVCUS01Y`/`CVACT01Y`/`CVACT02Y`/`CVACT03Y`/`CVTRA05Y`)의 상세 레이아웃은 별도 문서 및 에이전트 메모리(copybook_record_layouts) 참조. 본 문서는 export 변환과 출력 레이아웃(`CVEXPORT`)에 집중.
