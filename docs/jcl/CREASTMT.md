# CREASTMT — 카드 명세서(Statement) 생성 배치 잡

- **유형**: JCL
- **한 줄 요약**: XREF 파일에 등록된 카드별로 거래내역을 모아 텍스트(.PS) + HTML 두 형식의 월간 명세서를 생성하는 배치 잡. 명세서 생성 본체는 COBOL 프로그램 `CBSTM03A`가 담당한다.

## 기능 설명

이 잡은 야간 배치의 마지막 단계 격으로, 고객에게 발송할 **카드 명세서(account statement)** 를 생성한다. 잡 헤더 주석(`This JCL will create statement for each CARD present in the XREF file`, 19~20행)이 목적을 명시한다.

핵심 아이디어는 두 단계로 나뉜다.

1. **거래 데이터 재정렬/재적재 (STEP010~STEP020)**: 명세서는 "카드 단위"로 거래를 묶어야 한다. 그런데 마스터 거래 파일 `TRANSACT`의 기본 키는 거래 ID(TRAN-ID)다. 그래서 먼저 SORT로 거래를 **카드번호 + 거래ID** 순으로 재정렬하고, 카드번호를 선두에 둔 새 VSAM KSDS(`TRXFL`)를 만든다. 이 재배열된 파일이 있어야 `CBSTM03A`가 카드별로 거래를 순차 스캔할 수 있다. (Java 비유: 명세서 출력을 위해 트랜잭션을 `Map<카드번호, List<거래>>` 형태로 group-by 하는 전처리.)
2. **명세서 생성 (STEP040)**: `CBSTM03A`가 교차참조(XREF) 파일을 드라이빙 키로 돌면서, 각 카드에 대해 고객(CUST)/계정(ACCT) 정보와 위에서 만든 거래 파일을 조인하여 명세서 본문을 텍스트와 HTML로 동시에 출력한다.

DELDEF01·STEP030은 이전 실행 결과물(작업 파일, 직전 명세서 파일)을 지우고 새 데이터셋을 정의하는 **재실행 가능(restartable) 준비 단계**다.

`CBSTM03A`의 내부 동작(드라이버/포매터 + 범용 I/O 서브루틴 `CBSTM03B` 분담, ALTER/GO TO 디스패처, 메모리 내 2차원 카드/거래 테이블 조인, 텍스트+HTML 동시 출력)은 별도 프로그램 문서를 참조. 본 문서는 JCL(스텝/데이터셋 배선) 관점에 집중한다.

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|---------------|------|
| `DELDEF01` | `PGM=IDCAMS` (22행) | 작업용 파일 삭제 후 재정의. 순차 작업파일 `TRXFL.SEQ`와 KSDS `TRXFL.VSAM.KSDS`를 DELETE → `SET MAXCC=0`(없어도 무시) → KSDS를 DEFINE CLUSTER(키 길이 32 offset 0, RECORDSIZE 350, INDEXED). 즉 재정렬 결과를 담을 빈 그릇 준비. (28행 `SET MAXCC = 0`은 첫 실행 시 DELETE가 실패해도 잡을 계속 진행시키는 관용구.) |
| `STEP010` | `PGM=SORT` (44행) | `TRANSACT` KSDS를 읽어 **카드번호(263 위치, 16바이트) + 거래ID(1 위치, 16바이트)** 오름차순으로 정렬(53행). `OUTREC`로 카드번호를 레코드 선두(1:)로 옮겨 재배치한 350바이트 순차 파일 `TRXFL.SEQ` 생성(54행). |
| `STEP020` | `PGM=IDCAMS`, `COND=(0,NE)` (56행) | `REPRO`로 `TRXFL.SEQ`(순차) → `TRXFL.VSAM.KSDS`(KSDS) 적재(61행). 정렬된 순차 데이터를 인덱스 VSAM으로 변환. `COND=(0,NE)`=앞 스텝이 RC≠0이면 스킵. |
| `STEP030` | `PGM=IEFBR14`, `COND=(0,NE)` (66행) | 직전 실행의 명세서 출력물 삭제. `HTMLFILE`/`STMTFILE` DD를 `DISP=(MOD,DELETE,DELETE)`로 열었다 닫으며 `STATEMNT.HTML`·`STATEMNT.PS`를 제거. (IEFBR14는 아무 일도 안 하는 더미 프로그램 — DD의 DISP 부수효과만 노린다.) |
| `STEP040` | `PGM=CBSTM03A`, `COND=(0,NE)` (79행) | **명세서 생성 본체.** 입력 4파일(TRNX/XREF/ACCT/CUST)을 읽어 텍스트 명세서(`STMTFILE`, LRECL 80)와 HTML 명세서(`HTMLFILE`, LRECL 100)를 동시 생성. `STEPLIB`로 `CARDDEMO.LOADLIB`에서 로드모듈 검색(80행). |

## 의존성

- **COPY (PROC/INCLUDE)**:
  - 없음. 이 JCL은 cataloged PROC(`EXEC <procname>`)이나 `INCLUDE`를 사용하지 않고 모든 스텝을 인라인으로 정의한다.
- **호출 프로그램 (EXEC PGM)**:
  - `IDCAMS` — VSAM 유틸리티 (DELETE/DEFINE/REPRO). DELDEF01, STEP020.
  - `SORT` — 정렬 유틸리티 (DFSORT/SyncSort 계열). STEP010.
  - `IEFBR14` — 더미(no-op) 프로그램. STEP030 (DD DISP로 파일 삭제만 수행).
  - `CBSTM03A` — 명세서 생성 COBOL 프로그램. STEP040. (자기 자신은 STMTFILE/HTMLFILE만 직접 OPEN, 입력 4파일은 서브루틴 `CBSTM03B`를 통해 OPEN/READ/CLOSE — 별도 프로그램 문서 참조.)
- **데이터셋/파일/DB 테이블** (DD명 ↔ DSN ↔ 역할):
  - `AWS.M2.CARDDEMO.TRXFL.SEQ` — SORT 출력 순차 작업파일(350바이트 FB). STEP010이 생성(`SORTOUT`, 48행) → STEP020이 입력(`INFILE`, 58행)으로 소비. DELDEF01에서 사전 삭제.
  - `AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS` — 카드번호 선두 정렬된 거래 KSDS(키 32바이트, RECSIZE 350). DELDEF01이 정의 → STEP020이 적재(`OUTFILE`, 59행) → STEP040에서 `TRNXFILE` DD로 입력(83행). **중간 산출물**(이 잡 내부에서 생성·소비).
  - `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` — 마스터 거래 파일(원본 입력). STEP010 `SORTIN`(45행), `DISP=SHR` 읽기 전용.
  - `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` — 카드↔고객↔계정 교차참조. STEP040 `XREFFILE` DD(84행). copybook `CVACT03Y`(CARD-XREF-RECORD). **명세서 생성의 드라이빙 키**(여기 등록된 카드 단위로 명세서가 만들어짐).
  - `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` — 계정 마스터. STEP040 `ACCTFILE` DD(85행). copybook `CVACT01Y`(ACCOUNT-RECORD).
  - `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` — 고객 마스터. STEP040 `CUSTFILE` DD(86행). copybook `CVCUS01Y`(CUSTOMER-RECORD).
  - `AWS.M2.CARDDEMO.STATEMNT.PS` — 텍스트 명세서 **출력**(LRECL 80 FB). STEP040 `STMTFILE` DD(87~91행, `DISP=(NEW,CATLG,DELETE)`). STEP030에서 직전 분 사전 삭제.
  - `AWS.M2.CARDDEMO.STATEMNT.HTML` — HTML 명세서 **출력**(LRECL 100 FB). STEP040 `HTMLFILE` DD(92~96행). STEP030에서 사전 삭제.
  - DB2/IMS 테이블: 없음(베이스 앱은 VSAM만 사용).
  - 주의: 90행 `SPACE=(CYL,(1,1),RLSE), 00,RECFM=FB), ATA.VSAM.KSDS`에 깨진 잔여 텍스트가 보인다 — 편집 사고로 라인이 덮어써진 흔적으로 보이며, 실제 mainframe 제출 전 정리가 필요할 수 있다. (추측) 다만 `STMTFILE`의 핵심 파라미터(LRECL 80, DSN=...STATEMNT.PS)는 87~91행에서 정상적으로 지정되어 있다.
- **선행/후행 잡**:
  - 선행(전제): 마스터 파일들(`TRANSACT`, `CARDXREF`, `ACCTDATA`, `CUSTDATA`)이 최신 상태여야 함. 즉 야간 배치에서 마스터 갱신 → 거래 포스팅(POSTTRAN) → 이자계산(INTCALC) → 백업/결합(TRANBKP/COMBTRAN) 등이 끝난 뒤 실행되는 것이 자연스럽다. (추측: README의 "Running Batch Jobs" 시퀀스 및 `scripts/run_full_batch.sh`에는 CREASTMT가 명시적으로 포함되어 있지 않아, 정규 야간 사이클과 별개로 명세서 발행 시점에 단독 실행되는 잡으로 보인다.)
  - 후행: 생성된 `STATEMNT.PS`/`STATEMNT.HTML`을 배포/출력하는 다운스트림 처리(이 JCL 범위 밖).
  - 잡 내부 스텝 의존: DELDEF01 → STEP010 → STEP020 → STEP030 → STEP040 순서가 데이터로 강하게 연결됨(STEP010~020이 만든 `TRXFL.VSAM.KSDS`를 STEP040이 입력으로 사용). STEP020~040은 `COND=(0,NE)`로 앞 스텝 성공 시에만 진행.

## Java/현대화 노트

- **전처리 SORT = group-by**: STEP010의 SORT+OUTREC(거래를 카드번호로 재정렬하고 키 필드를 선두로 이동)는 본질적으로 "명세서 출력을 위한 group-by/정렬"이다. 현대 Spring Batch라면 별도 KSDS를 만드는 대신, `TRANSACT`를 카드번호로 정렬한 스트림을 직접 읽어 카드 단위로 청크를 끊거나(`ItemReader` + 카드 경계 검출), DB라면 `ORDER BY card_number, tran_id` 쿼리 한 방으로 대체한다. 중간 산출물 `TRXFL.*`(SEQ→KSDS 2단 변환)는 mainframe에서 "순차 정렬 결과를 키 접근 가능한 VSAM으로 바꾸는" 비용 때문에 생긴 구조이며, RDB/객체 스토리지 기반에서는 불필요해진다.
- **IDCAMS DELETE + SET MAXCC=0 = idempotent setup**: DELDEF01의 "삭제 실패 무시 후 재정의" 패턴은 Java에서 "출력 파일이 있으면 지우고 새로 만든다"(`Files.deleteIfExists` 후 생성)에 해당한다. 잡을 몇 번 돌려도 같은 결과가 나오도록 보장하는 멱등 준비 단계.
- **IEFBR14 + DISP=(MOD,DELETE,DELETE) = 파일 청소 트릭**: STEP030은 "프로그램은 아무 것도 안 하고, DD의 DISP 부수효과로만 파일을 지우는" mainframe 관용구다. MOD로 (없으면 만들었다가) 열고, 끝에서 DELETE. Java에는 직접 대응이 없고, 그냥 `Files.deleteIfExists(htmlPath); Files.deleteIfExists(psPath);` 한 줄이면 된다. 이런 "유틸리티 프로그램으로 파일 라이프사이클을 조작하는" 패턴이 JCL 곳곳에 있다는 점을 인지할 것.
- **DD명 ↔ 프로그램 SELECT/ASSIGN 결합**: STEP040의 `TRNXFILE`/`XREFFILE`/`ACCTFILE`/`CUSTFILE`/`STMTFILE`/`HTMLFILE` DD명은 `CBSTM03A`(및 `CBSTM03B`) 내부 `SELECT ... ASSIGN TO <DD명>`과 정확히 1:1로 묶인다. 즉 JCL이 "외부 설정으로 파일 경로를 주입"하는 역할 — Java로 옮길 때 이 DD명들이 곧 설정 키(application.yml의 input/output 경로, 또는 DataSource/리소스 핸들)가 된다. DD명을 바꾸면 프로그램도 함께 바꿔야 한다.
- **두 출력 형식 동시 생성**: 같은 비즈니스 데이터를 텍스트(80바이트 고정폭)와 HTML(100바이트) 두 형식으로 한 번의 패스에서 만든다. 현대화 시에는 도메인 모델(Statement 객체)을 한 번 만들고 텍스트 렌더러 / HTML 렌더러(예: Thymeleaf) 두 개를 적용하는 식으로 출력 책임을 분리하면 자연스럽다.
- **고정폭 레코드 길이 주의**: 출력은 RECFM=FB 고정 길이(텍스트 80, HTML 100)다. COBOL 측 라인이 이 폭에 정확히 맞춰 편집되어 있으므로(zero-suppress, 후행 부호 등), Java로 옮길 때 텍스트 명세서는 단순 문자열이 아니라 컬럼 정렬된 고정폭 포맷임을 유지해야 한다(다운스트림이 컬럼 위치에 의존할 수 있음).
- **TIME=1440 / CLASS=A**: 잡 카드(1~2행)의 `TIME=1440`은 사실상 무제한 CPU 시간(분 단위)으로, 데이터량에 따라 장시간 실행될 수 있음을 시사한다. 현대 배치에서는 타임아웃/리소스 한도를 명시적으로 관리.
