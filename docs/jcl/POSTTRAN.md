# POSTTRAN — 일일 거래 입력(Posting) 배치 잡

- **유형**: JCL
- **한 줄 요약**: 일일 거래 파일(`DALYTRAN.PS`)을 읽어 카드→계정 교차참조와 신용한도/만료일을 검증한 뒤, 통과 건은 거래 마스터·카테고리 잔액·계정 잔액에 반영(posting)하고 실패 건은 거절 파일로 분리하는 야간 배치의 핵심 거래 반영 단계.

## 기능 설명

JCL 헤더 주석이 잡의 목적을 명시한다: "Process and load daily transaction file and create transaction category balance and update transaction master vsam" (L20-21). 즉 하루치 거래를 받아 (1) 거래 마스터 KSDS에 적재하고 (2) 거래 카테고리별 잔액을 생성/갱신하며 (3) 계정 잔액을 갱신하는 일이다.

이 잡은 단일 스텝 `STEP15`로 구성되며 `PGM=CBTRN02C`(L23)를 실행한다. CBTRN02C는 CICS를 거치지 않는 순수 배치 프로그램으로, `FILE-CONTROL`의 `SELECT ... ASSIGN TO`(CBTRN02C.cbl L29-61)를 통해 VSAM/순차 파일을 직접 연다. 따라서 JCL의 각 DD명이 COBOL의 ASSIGN 대상명과 1:1로 연결된다(예: JCL `//DALYTRAN DD` ↔ `ASSIGN TO DALYTRAN`).

처리 흐름(프로그램 내부, 메모리상 검증된 로직 기준)은 일일 거래 한 건마다:
1. `1500-VALIDATE-TRAN` — XREF(`XREFFILE`)로 카드번호→계정ID 조회 후, 계정의 신용한도·만료일을 검증.
2. 검증 통과 시 `2000-POST-TRANSACTION` — 거래 카테고리 잔액 갱신(없으면 신규 생성), 계정의 현재 잔액 및 사이클 차변/대변 갱신, 거래 마스터(`TRANFILE`)에 기록.
3. 검증 실패 시 거절 사유 코드와 함께 거절 파일(`DALYREJS`)에 기록.

Java/OOP 관점에서 이 잡 자체는 "배치 실행 디스크립터(실행 구성)"에 해당한다. JCL은 어떤 프로그램을(`PGM=`) 어떤 입출력 리소스(`DD`)에 바인딩해 돌릴지를 선언할 뿐, 실제 비즈니스 로직은 COBOL 프로그램(CBTRN02C)에 있다. Spring Batch로 보면 JCL ≈ Job/Step 정의 + 리소스 주입(파일 경로 바인딩), CBTRN02C ≈ Reader/Processor/Writer 로직에 해당한다.

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|---------------|------|
| STEP15 | `PGM=CBTRN02C` (L23) | 일일 거래 검증 및 posting. 입력 거래를 읽어 XREF/계정 검증 후, 통과 건은 거래 마스터·카테고리 잔액·계정 잔액에 반영하고 실패 건은 거절 파일로 분리. |

단일 스텝 잡이다(스텝은 STEP15 하나뿐). 스텝명이 `STEP15`인 이유는 이 잡이 야간 배치 시퀀스 전체에서 차지하는 순번을 반영한 관례로 보인다(추측).

## 의존성

- **COPY (PROC/INCLUDE)**:
  - 없음. 이 JCL은 카탈로그 프로시저(`EXEC PROC=`)나 `INCLUDE` 멤버를 호출하지 않고, 인라인 `EXEC PGM=`(L23) 한 개로만 구성된다.

- **호출 프로그램 (EXEC PGM)**:
  - `CBTRN02C` (L23) — 거래 입력/posting 배치 프로그램. 로드 모듈은 `STEPLIB`인 `AWS.M2.CARDDEMO.LOADLIB`(L24-25)에서 적재.

- **데이터셋/파일/DB 테이블**: (DD명 ↔ COBOL ASSIGN명 ↔ 데이터셋 ↔ 용도)

  | DD명 (JCL) | COBOL 내부 파일 (ASSIGN) | 데이터셋 (DSN) | DISP | 조직/접근 | 용도 |
  |-----------|--------------------------|----------------|------|-----------|------|
  | `STEPLIB` (L24) | — | `AWS.M2.CARDDEMO.LOADLIB` | SHR | PDS(로드) | 실행 로드 모듈 라이브러리 |
  | `SYSPRINT` (L26) | — | `SYSOUT=*` | — | 스풀 | 시스템/컴파일러 런타임 출력 |
  | `SYSOUT` (L27) | — | `SYSOUT=*` | — | 스풀 | 프로그램 `DISPLAY` 출력(콘솔/로그) |
  | `TRANFILE` (L28-29) | `TRANSACT-FILE` (ASSIGN TO TRANFILE) | `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | SHR | INDEXED / RANDOM | 거래 마스터 KSDS. posting 통과 건을 기록(출력 성격이나 RANDOM 접근으로 WRITE). |
  | `DALYTRAN` (L30-31) | `DALYTRAN-FILE` (ASSIGN TO DALYTRAN) | `AWS.M2.CARDDEMO.DALYTRAN.PS` | SHR | SEQUENTIAL | 입력: 하루치 일일 거래 순차 파일(드라이빙 파일). |
  | `XREFFILE` (L32-33) | `XREF-FILE` (ASSIGN TO XREFFILE) | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` | SHR | INDEXED / RANDOM | 카드번호↔계정 교차참조. 카드→계정ID 룩업용(읽기). |
  | `DALYREJS` (L34-38) | `DALYREJS-FILE` (ASSIGN TO DALYREJS) | `AWS.M2.CARDDEMO.DALYREJS(+1)` | NEW,CATLG,DELETE | SEQUENTIAL (RECFM=F, LRECL=430) | 출력: 검증 실패 거래를 거절 사유와 함께 기록하는 신규 GDG 세대. |
  | `ACCTFILE` (L39-40) | `ACCOUNT-FILE` (ASSIGN TO ACCTFILE) | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | SHR | INDEXED / RANDOM | 계정 마스터. 잔액·사이클 차변/대변 갱신(I-O, 읽고 REWRITE). |
  | `TCATBALF` (L41-42) | `TCATBAL-FILE` (ASSIGN TO TCATBALF) | `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS` | SHR | INDEXED / RANDOM | 거래 카테고리별 잔액. 갱신 또는 신규 생성(I-O). |

  주의: 이전 메모리 노트는 거래 마스터의 DD명을 `TRANSACT`로 적었으나, 실제 JCL의 DD명은 `TRANFILE`이고 COBOL 내부 파일명이 `TRANSACT-FILE`이다(CBTRN02C.cbl L34 `SELECT TRANSACT-FILE ASSIGN TO TRANFILE`로 확인). 본 문서의 매핑이 정확하다.

- **선행/후행 잡**:
  - 야간 배치 시퀀스(root README "Running Batch Jobs" = `scripts/run_full_batch.sh`)에서 이 잡의 위치는 다음과 같다(메모리 기준):
    - 선행: 마스터 파일 갱신 잡들(ACCTFILE/CARDFILE/XREFFILE/CUSTFILE 등 적재) — POSTTRAN이 읽는 계정·교차참조 마스터가 먼저 최신 상태여야 함.
    - 후행: `INTCALC`(CBACT04C 이자 계산) → `TRANBKP` → `COMBTRAN`(SORT) → `TRANIDX`(AIX 정의) → `OPENFIL`(CICS에 파일 재오픈).
  - 또한 CICS가 잡고 있는 VSAM을 배치 전에 닫는 `CLOSEFIL`, 후에 다시 여는 `OPENFIL`이 배치 사이클 전후를 감싼다. POSTTRAN은 ACCTDATA/TRANSACT/TCATBALF/CARDXREF 등 VSAM에 갱신을 가하므로 CICS와의 동시 접근을 피하려면 `CLOSEFIL` 이후, `OPENFIL` 이전에 실행되어야 한다(추측: 시퀀스 관례에 근거).

## Java/현대화 노트

- **JCL = 실행 구성(Job/Step) 선언, 로직은 별도**: 이 JCL에는 비즈니스 로직이 없다. `PGM=CBTRN02C` + 7개 데이터 DD 바인딩이 전부다. Spring Batch로 옮기면 이 파일은 `Job` 한 개 + `Step`(STEP15) 한 개의 정의로 축소되고, 7개 DD는 `Resource`(파일 경로)·`DataSource`(KSDS→RDB 테이블) 주입으로 대체된다. JCL은 "무엇을 어디에 연결해 돌릴지"만 선언하는 IaC/구성 파일에 가깝다.

- **DD명이 곧 의존성 주입 키**: COBOL `ASSIGN TO XREFFILE`는 물리 파일을 직접 열지 않고 논리명(`XREFFILE`)만 지정하며, 실제 데이터셋은 JCL DD가 런타임에 바인딩한다. 이는 Java의 생성자/세터 주입이나 `@ConfigurationProperties`로 외부 리소스를 주입하는 것과 정확히 같은 발상이다. 따라서 단위 테스트 시 입력 `DALYTRAN.PS`만 교체하면 동일 로직을 다른 데이터로 돌릴 수 있다(테스트 더블 주입과 동형).

- **DISP가 트랜잭션/멱등성 의미를 가짐**:
  - `DISP=SHR`(공유 읽기/갱신)와 `DISP=(NEW,CATLG,DELETE)`(신규 생성, 정상 종료 시 카탈로그·실패 시 삭제)는 단순 파일 모드가 아니라 실행 의미론이다. 특히 `DALYREJS(+1)`(L38)은 GDG(세대 데이터 그룹)의 `+1` 상대 세대로, 실행할 때마다 새 세대를 만들어 거절 내역의 이력을 보존한다 — Java에서 타임스탬프/시퀀스로 출력 파일을 버전닝하거나 append-only 거절 테이블에 INSERT하는 것에 대응.
  - `DISP=(...,DELETE)`의 실패 시 삭제는 "스텝 abend 시 부분 출력 롤백"에 해당한다. 단, `DISP=SHR`로 갱신하는 ACCTFILE/TRANFILE/TCATBALF는 자동 롤백되지 않는다 — VSAM 직접 갱신은 트랜잭션이 아니므로, 잡이 중간에 죽으면 일부만 반영된 상태가 남는다. Java 이식 시 이 부분은 반드시 명시적 트랜잭션 경계(@Transactional, 청크 단위 커밋)로 감싸야 하는 위험 지점이다.

- **RANDOM 접근 KSDS는 인덱스 룩업/업서트**: TRANFILE·XREFFILE·ACCTFILE·TCATBALF가 모두 `ACCESS MODE IS RANDOM`(CBTRN02C.cbl L36/42/53/59)이다. 즉 키(계정ID/카드번호/카테고리키)로 직접 READ/REWRITE/WRITE 한다. Java로는 `Map<Key, Record>` 또는 RDB `SELECT ... WHERE pk = ?` + `UPDATE`/`INSERT`(업서트)로 매핑된다. TCATBALF의 "없으면 생성, 있으면 갱신"은 전형적인 upsert 패턴이다.

- **거절 레코드 길이 430바이트 고정**: `DALYREJS` DCB가 `RECFM=F,LRECL=430`(L36)이다. 거절 파일 레이아웃(원본 일일거래 레코드 + 거절 사유 필드)이 정확히 430바이트 고정폭임을 의미한다. Java로는 고정폭 플랫 파일(FlatFileItemWriter, FixedLength)로 그대로 매핑하거나, 거절 사유를 별도 컬럼으로 둔 테이블로 정규화할 수 있다.

- **에러 처리/리턴코드 규약(메모리 기준)**: CBTRN02C는 각 I/O 후 FILE STATUS 2바이트를 검사해 정상('00')/EOF('10')/오류 분기하며, 치명적 오류 시 `9999-ABEND-PROGRAM`이 `CALL 'CEE3ABD'`로 강제 abend, reject 발생 시 RETURN-CODE를 4로 설정한다. JCL 레벨에서 후속 스텝 `COND`/`IF` 분기는 이 RC에 의존할 수 있다(이 단일 스텝 JCL에는 COND가 없으므로 시퀀스 전체 잡 흐름에서 처리될 것으로 추정 — 추측). Java에서는 `ExitStatus`/예외로 매핑하고, 거절은 abend가 아니라 정상 종료 + skip/거절 라우팅으로 처리하는 것이 idiomatic하다.

- **`MSGCLASS=0`(L1)**: 잡 메시지 출력 클래스 지정. 기능 로직과 무관한 운영 파라미터로, Java 이식 시 로깅 설정(레벨/appender)에 해당한다.
