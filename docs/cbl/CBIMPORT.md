# CBIMPORT — 지점 마이그레이션 고객 데이터 가져오기 배치

- **유형**: 배치 COBOL
- **한 줄 요약**: 지점 마이그레이션용 단일 다중-레코드 export 파일을 읽어, 레코드 타입(C/A/X/T/D)에 따라 고객·계정·교차참조·거래·카드 5개 정규화 출력 파일로 분리(언번들)하고 통계를 출력하는 ETL 배치.

## 기능 설명

CBIMPORT는 지점에서 추출(export)한 **하나의 가변 의미를 가진 고정 500바이트 레코드 파일**(`EXPORT-INPUT`)을 순차적으로 읽어, 각 레코드의 첫 바이트(`EXPORT-REC-TYPE`)로 종류를 판별한 뒤 해당 종류에 맞는 출력 파일로 변환·기록한다(소스 8-12, 28-32행). 한 입력 파일 안에 고객/계정/카드교차참조/거래/카드 다섯 종류 레코드가 섞여 있고, 이를 다섯 개의 정규화된 출력 파일로 "흩뿌리는(scatter/split)" 전형적인 **언번들링 ETL** 패턴이다.

핵심은 `CVEXPORT` 카피북의 **REDEFINES 공용체(union)** 구조다. 460바이트짜리 공통 데이터 영역(`EXPORT-RECORD-DATA`)을 다섯 가지 서로 다른 레이아웃으로 겹쳐 해석한다(CVEXPORT 19-100행). 즉 같은 바이트열을 레코드 타입에 따라 "고객 레코드로", "계정 레코드로" 다르게 읽는 것이며, 이는 Java의 직접 등가물이 없는 메모리 오버레이 기법이다(아래 마이그레이션 노트 참조).

검증(`3000-VALIDATE-IMPORT`)과 체크섬 언급(주석 31행, 카피북 6행)이 있으나 **실제 검증 로직은 비어 있고** 화면에 "오류 없음"만 출력한다(소스 449-452행). 즉 헤더 주석의 "체크섬 무결성 검증"은 현재 미구현이다.

## 입력 / 출력

- **입력**:
  - `EXPORT-INPUT` (DD명 `EXPFILE`) — 다중 레코드 export 파일. 레코드 길이 500, `RECORDING MODE IS F`(고정길이)(소스 76-79행). SELECT에는 `ORGANIZATION IS INDEXED` + `RECORD KEY IS EXPORT-SEQUENCE-NUM`으로 선언(37-41행)되어 있으나 접근은 `ACCESS MODE IS SEQUENTIAL`이고 본문은 순차 READ만 한다(250-256, 261행). → 사실상 키 순(시퀀스 번호 오름차순) 순차 입력. (이 INDEXED 선언과 단순 순차 처리의 불일치는 마이그레이션 시 확인 필요.)
- **출력**:
  - `CUSTOMER-OUTPUT` (DD `CUSTOUT`, 500바이트, `CVCUS01Y`/`CUSTOMER-RECORD`) — 고객 레코드(소스 81-84, 312행)
  - `ACCOUNT-OUTPUT` (DD `ACCTOUT`, 300바이트, `CVACT01Y`/`ACCOUNT-RECORD`) — 계정 레코드(86-89, 341행)
  - `XREF-OUTPUT` (DD `XREFOUT`, 50바이트, `CVACT03Y`/`CARD-XREF-RECORD`) — 카드-계정-고객 교차참조(91-94, 361행)
  - `TRANSACTION-OUTPUT` (DD `TRNXOUT`, 350바이트, `CVTRA05Y`/`TRAN-RECORD`) — 거래 레코드(96-99, 391행)
  - `CARD-OUTPUT` (DD `CARDOUT`, 150바이트, `CVACT02Y`/`CARD-RECORD`) — 카드 레코드(101-104, 414행)
  - `ERROR-OUTPUT` (DD `ERROUT`, 132바이트) — 파이프(`|`) 구분 오류 리포트. 알 수 없는 레코드 타입 기록(106-109, 152-160, 439행)
  - SYSOUT(`DISPLAY`) — 가져오기 시작/종료 메시지 및 통계 카운터(176, 451-478행)

## 의존성

- **COPY (카피북)**:
  - `CVEXPORT` (WORKING-STORAGE, 113행) — **입력** export 레코드의 5중 REDEFINES 공용체 레이아웃. 본 프로그램의 핵심.
  - `CVCUS01Y` (FD, 84행) — 고객 출력 레코드(`CUSTOMER-RECORD`)
  - `CVACT01Y` (FD, 89행) — 계정 출력 레코드(`ACCOUNT-RECORD`)
  - `CVACT03Y` (FD, 94행) — 교차참조 출력 레코드(`CARD-XREF-RECORD`)
  - `CVTRA05Y` (FD, 99행) — 거래 출력 레코드(`TRAN-RECORD`)
  - `CVACT02Y` (FD, 104행) — 카드 출력 레코드(`CARD-RECORD`)
- **호출 프로그램 (CALL/XCTL/LINK)**:
  - `CEE3ABD` (484행) — Language Environment 콜러블 서비스. 비정상 종료(ABEND) 강제. 애플리케이션 서브프로그램이 아닌 시스템 런타임 루틴.
- **데이터셋/파일/DB 테이블**:
  - 위 입력/출력 항목 참조. DB(DB2 등) 접근은 없음 — 순수 파일 to 파일 배치.
- **트랜잭션 ID 또는 EXEC PGM**:
  - CICS 트랜잭션 아님. JCL에서 `EXEC PGM=CBIMPORT`로 실행되는 배치(소스 7행 "BATCH COBOL Program"). (해당 JCL 멤버는 본 소스에 없음 — 별도 확인 필요/추측)

## 핵심 로직 흐름

`0000-MAIN-PROCESSING`은 4단계 PERFORM 후 `GOBACK`하는 표준 배치 골격이다(165-171행). 배치이므로 `GOBACK`은 사실상 운영체제로의 정상 복귀(`STOP RUN`과 동등하게 동작).

1. **1000-INITIALIZE** (174행): 시작 메시지 출력 → `FUNCTION CURRENT-DATE`를 잘라 `YYYY-MM-DD`/`HH:MM:SS` 형태의 가져오기 일시 문자열을 조립(178-188행) → `1100-OPEN-FILES`로 7개 파일을 모두 OPEN. 어느 하나라도 FILE STATUS가 '00'이 아니면 즉시 `9999-ABEND-PROGRAM`으로 ABEND(196-245행). (조립한 일시는 화면 출력에만 쓰이고 레코드에는 기록되지 않음.)

2. **2000-PROCESS-EXPORT-FILE** (248행): 선읽기(priming read) 후 `PERFORM UNTIL WS-EXPORT-EOF` 루프. 매 반복마다 총 읽기 카운터 +1 → `2200-PROCESS-RECORD-BY-TYPE` → 다음 레코드 읽기(250-256행). EOF는 FILE STATUS '10'으로 판별(118행). `READ ... INTO EXPORT-RECORD`로 FD의 익명 영역을 WORKING-STORAGE의 공용체 구조로 복사해 해석(261행).

3. **2200-PROCESS-RECORD-BY-TYPE** (270행): `EVALUATE EXPORT-REC-TYPE`로 분기(272-285행) — Java `switch`와 동일.
   - `'C'` → 고객(`2300`), `'A'` → 계정(`2400`), `'X'` → 교차참조(`2500`), `'T'` → 거래(`2600`), `'D'` → 카드(`2650`), 그 외 → `2700-PROCESS-UNKNOWN-RECORD`.
   - 카드(D)와 교차참조(X)의 타입 문자가 다른 점에 유의(카드 = 'D', 교차참조 = 'X').

4. **각 2300~2650 처리 단락**: 패턴이 동일하다 — ① 대상 출력 레코드 `INITIALIZE`(공백/0 초기화) → ② export 공용체 뷰의 `EXP-*` 필드를 대상 레코드의 동명 비-EXP 필드로 일대일 `MOVE`(필드 매핑) → ③ `WRITE` → ④ FILE STATUS 확인, 실패 시 ABEND → ⑤ 해당 타입 임포트 카운터 +1.
   - 고객(2300, 288행): 17개 필드 매핑. 주소 3줄(`OCCURS 3`)과 전화번호 2개(`OCCURS 2`)는 첨자 (1)(2)(3)로 개별 평탄화(297-304행).
   - 계정(2400, 323행): 12개 필드(잔액·한도 등 금액 포함).
   - 교차참조(2500, 352행): 카드번호/고객ID/계정ID 3개만.
   - 거래(2600, 372행): 13개 필드(가맹점 정보·타임스탬프 포함). 카운터는 `WS-TRAN-RECORDS-IMPORTED`(399행).
   - 카드(2650, 402행): 6개 필드.

5. **2700/2750 알 수 없는 타입**: 카운터 +1 → 오류 레코드(타임스탬프|타입|시퀀스|메시지) 조립 → `2750-WRITE-ERROR`로 `ERROR-OUTPUT`에 기록(425-446행). 주의: 오류 파일 WRITE 실패 시 메시지만 출력하고 **ABEND하지 않음**(다른 출력과 다른 처리, 441-444행).

6. **3000-VALIDATE-IMPORT** (449행): 현재 실질 로직 없이 "검증 완료/오류 없음" 메시지만 출력(스텁).

7. **4000-FINALIZE** (455행): 7개 파일 CLOSE → 총 읽기/각 타입 임포트 건수/오류 건수/미지 타입 건수 통계를 `DISPLAY`로 출력(457-478행).

8. **9999-ABEND-PROGRAM** (481행): ABEND 메시지 출력 후 `CALL 'CEE3ABD'`로 강제 비정상 종료(잔여 파일 CLOSE 없이 즉시 중단).

## Java/현대화 노트

- **REDEFINES 공용체 = 다형 레코드 파싱**. `EXPORT-RECORD-DATA`(460바이트) 위에 5개 레이아웃을 겹친 구조는 Java의 직접 등가물이 없다. 권장 패턴: 입력을 `byte[]`(또는 `ByteBuffer`) 1줄로 받아 1바이트 타입 코드를 읽고, `switch`로 분기해 **타입별 전용 파서/DTO**(`CustomerRecord`, `AccountRecord`, `XrefRecord`, `TranRecord`, `CardRecord`)로 디코딩하라. COBOL은 "어느 REDEFINES 뷰가 유효한지" 런타임 안전장치가 없어 타입 코드와 실제 바이트 해석의 정합성을 전적으로 데이터 생산자(export 측)에 의존한다 — Java에서는 sealed interface + 타입별 record로 이 계약을 명시화할 수 있다.

- **EVALUATE → switch**. `2200`의 타입 분기는 enum(`RecordType.C/A/X/T/D`) + `switch`로 자연 변환된다. `WHEN OTHER`는 default 분기(미지 타입 → 오류 로그)로 매핑.

- **숫자 저장 포맷 주의(가장 큰 함정)**. export 공용체의 `EXP-*` 필드는 출력 카피북과 **PIC/USAGE가 미묘하게 다르다**. 같은 의미의 필드가 입력은 DISPLAY, 출력은 COMP-3인 식의 비대칭이 있으며, COBOL은 `MOVE` 시 자동 변환해 주지만 Java에서는 직접 디코딩해야 한다. 대표 사례:
  - `EXPORT-SEQUENCE-NUM PIC 9(9) COMP` (CVEXPORT 16행) → 4바이트 빅엔디언 부호없는 정수. Java `int`/`long` + `ByteBuffer.getInt`.
  - `EXP-CUST-ID PIC 9(09) COMP`, `EXP-CARD-ACCT-ID PIC 9(11) COMP`, `EXP-CARD-CVV-CD PIC 9(03) COMP`, `EXP-TRAN-MERCHANT-ID PIC 9(09) COMP`, `EXP-XREF-ACCT-ID PIC 9(11) COMP` → 모두 **이진(COMP)** 정수. 바이트 폭은 자리수에 따라 결정(9자리·11자리=4바이트, 3자리=2바이트). 반면 같은 export 안의 `EXP-CUST-SSN PIC 9(09)`, `EXP-XREF-CUST-ID PIC 9(09)`, `EXP-TRAN-CAT-CD PIC 9(04)` 등은 **DISPLAY(존드 ASCII/EBCDIC 숫자)**라 디코딩 방식이 다름. 필드마다 USAGE를 반드시 확인할 것.
  - `EXP-CUST-FICO-CREDIT-SCORE PIC 9(03) COMP-3` → 패킹 십진수(2바이트). nibble 단위 디코딩 필요.
  - 금액 필드는 **COMP-3와 DISPLAY가 혼재**: `EXP-ACCT-CURR-BAL`/`EXP-ACCT-CASH-CREDIT-LIMIT`/`EXP-TRAN-AMT`는 `S9(...)V99 COMP-3`(패킹), `EXP-ACCT-CREDIT-LIMIT`/`EXP-ACCT-CURR-CYC-CREDIT`는 `S9(10)V99`(DISPLAY), `EXP-ACCT-CURR-CYC-DEBIT`는 `S9(10)V99 COMP`(이진!). 모두 **암시 소수점 V99**이므로 Java에서는 예외 없이 `BigDecimal`(scale=2)로 매핑하고 double/float는 금지. 특히 `COMP`로 선언된 소수 금액(`CURR-CYC-DEBIT`)은 "스케일된 정수를 100으로 나눈" 값임에 주의.

- **OCCURS → 배열/List**. 고객의 주소 3줄(`EXP-CUST-ADDR-LINE OCCURS 3`)과 전화 2개(`EXP-CUST-PHONE-NUM OCCURS 2`)는 Java `String[]`/`List<String>`. COBOL 첨자는 **1-based**(소스 297-304행의 (1)(2)(3))이므로 Java 0-based로 옮길 때 인덱스 오프셋 주의.

- **고정길이/FILLER 패딩**. 모든 출력은 고정 길이 레코드이고 미사용 영역은 FILLER로 공백/제로 채움. Java에서 동일 바이너리 파일을 재생성하려면 각 필드의 정확한 바이트 폭·정렬·패딩 문자(공백 vs 0)를 보존해야 한다. 단순 텍스트 직렬화로는 호환되지 않음.

- **에러 처리 일관성 부재**. 비즈니스 출력 파일(고객~카드) WRITE 실패는 ABEND이지만 오류 리포트 파일 WRITE 실패는 무시(로그만)된다(441-444행). 또한 ABEND 경로(`9999`)는 파일을 닫지 않아 출력이 불완전/미플러시 상태로 남을 수 있다. Java로 옮길 때는 try-with-resources + 일관된 예외 정책으로 정리하는 것이 바람직.

- **검증/체크섬 미구현**. 헤더와 카피북 주석이 "체크섬 무결성 검증"을 표방하나 `3000`은 스텁이다(449-452행). 현대화 시 실제 무결성 검증(레코드 수/합계/체크섬 대조)을 구현할 자리.

- **INDEXED vs SEQUENTIAL 불일치**. 입력 SELECT가 `ORGANIZATION IS INDEXED`(KSDS)로 선언되었으나 순차 처리만 한다(37-41행). export 파일이 실제 VSAM KSDS인지 단순 시퀀셜인지 JCL/IDCAMS로 확인 필요. Java에서는 단순 순차 스트림(키 순서 정렬 전제)으로 간주하면 충분해 보인다(추측).

- **CEE3ABD = 의도적 비정상 종료**. LE 콜러블 서비스로 강제 ABEND를 일으켜 JCL `COND`/스케줄러가 후속 스텝을 막도록 하는 관용구. Java에서는 비영(非零) 종료코드(`System.exit(non-zero)`) 또는 잡 실패 예외로 대응.
