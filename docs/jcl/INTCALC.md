# INTCALC — 이자 계산 배치 잡

- **유형**: JCL
- **한 줄 요약**: 처리일자를 PARM으로 받아 `CBACT04C`를 실행, 거래 카테고리 잔액 파일을 스캔하며 계정별 이자를 계산하고 이자 거래를 신규 거래 파일에 생성한다.

## 기능 설명

이 JCL은 야간 배치 사이클 중 **이자 계산** 단계를 수행하는 단일 스텝 잡이다. 잡 헤더의 주석(line 20)이 그 목적을 명시한다: `Process transaction balance file and compute interest and fees.`

핵심 동작은 다음과 같다(상세 로직은 호출 프로그램 `CBACT04C`에 있음):

- `STEP15`가 `PGM=CBACT04C`를 실행하면서 `PARM='2022071800'`(line 22)으로 **처리 기준일자**를 넘긴다. 이 10바이트 값은 `YYYYMMDD` + 트레일링 `00` 형태로, COBOL 프로그램은 이를 `LINKAGE SECTION`의 `EXTERNAL-PARMS`(길이 `S9(4) COMP` + 날짜 `X(10)`)로 수신한다. Java로 보면 `main(String[] args)`의 인자로 실행일자 문자열을 받는 것과 동등하다.
- 프로그램은 거래 카테고리 잔액 파일(`TCATBALF`)을 계정ID 오름차순으로 순차 스캔하면서 **control-break**(계정ID가 바뀌는 지점) 단위로 누적 이자를 집계한다.
- 각 카테고리 행마다 디스클로저 그룹(`DISCGRP`)에서 연이자율을 조회하고 `COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200`(월이자 = 잔액 × 연율% / 100 / 12)로 이자를 계산한다.
- 계정이 바뀌면 직전 계정의 누적 이자를 계정 잔액(`ACCTFILE`)에 더해 `REWRITE`하고, 계산된 이자 거래는 신규 출력 파일(`TRANSACT`)에 `WRITE`한다.

> 참고: PARM 날짜는 하드코딩된 `2022071800`이다. 실제 운영에서는 스케줄러나 심볼릭 치환으로 당일 일자를 주입해야 하며, 현재 값은 샘플/테스트용 고정값이다. (추측)

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|---------------|------|
| `STEP15` | `PGM=CBACT04C` (PARM=`2022071800`) | 거래 카테고리 잔액 파일을 스캔해 계정별 이자를 계산하고, 계정 잔액을 갱신하며 이자 거래를 신규 거래 파일에 생성 (line 22) |

> 잡 이름은 `INTCALC`이지만 스텝 번호가 `STEP15`인 점, 그리고 PROC 안에 단계가 없는 점으로 보아, 이 잡은 단일 스텝만 갖는다(line 22가 유일한 `EXEC`). 스텝 번호 15는 더 큰 배치 흐름 내 순번을 암시하는 명명 관례로 추정된다. (추측)

## 의존성

- **COPY (PROC/INCLUDE)**:
  - 없음 — 이 잡은 카탈로그 PROC(`EXEC <procname>`)이나 `INCLUDE` 멤버를 호출하지 않고, 모든 DD를 인라인으로 정의한다.

- **호출 프로그램 (EXEC PGM)**:
  - `CBACT04C` — 이자/수수료 계산 배치 프로그램 (line 22). LOADLIB에서 로드됨(`STEPLIB DD ... AWS.M2.CARDDEMO.LOADLIB`, line 23-24).

- **데이터셋/파일/DB 테이블** (DD ↔ DSN ↔ 용도):

  | DD명 | DSN | DISP | 용도 |
  |------|-----|------|------|
  | `STEPLIB` | `AWS.M2.CARDDEMO.LOADLIB` | SHR | `CBACT04C` 실행 모듈(로드 라이브러리) (line 23-24) |
  | `SYSPRINT` | `SYSOUT=*` | — | 시스템/런타임 출력 (line 25) |
  | `SYSOUT` | `SYSOUT=*` | — | 프로그램 표시(DISPLAY)/오류 메시지 출력 (line 26) |
  | `TCATBALF` | `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS` | SHR | **입력(드라이빙)** — 거래 카테고리 잔액 KSDS, 계정ID 순으로 순차 스캔 (line 27-28) |
  | `XREFFILE` | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` | SHR | 입력 — 카드↔계정 교차참조 KSDS (line 29-30) |
  | `XREFFIL1` | `AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH` | SHR | 입력 — 카드 교차참조의 대체 인덱스(AIX) PATH, 계정ID로 조회하기 위한 경로 (line 31-32) |
  | `ACCTFILE` | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | SHR | **입출력(I-O)** — 계정 마스터 KSDS, 누적 이자를 잔액에 반영해 `REWRITE` (line 33-34) |
  | `DISCGRP` | `AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS` | SHR | 입력 — 디스클로저 그룹(이자율 마스터) KSDS (line 35-36) |
  | `TRANSACT` | `AWS.M2.CARDDEMO.SYSTRAN(+1)` | `(NEW,CATLG,DELETE)` | **출력** — 생성된 이자 거래를 기록하는 신규 순차 데이터셋(GDG +1세대), `RECFM=F`, `LRECL=350` (line 37-41) |

  > 메모: `XREFFILE`(베이스 클러스터)과 `XREFFIL1`(AIX PATH)은 동일한 `CARDXREF` VSAM을 가리킨다. 베이스는 카드번호 키로, AIX PATH는 계정ID 키로 접근하기 위한 이중 정의다. COBOL의 `SELECT ... ALTERNATE RECORD KEY`에 대응한다.
  >
  > `TRANSACT`의 `DSN=...SYSTRAN(+1)`은 **GDG(세대 데이터 그룹)** 표기로, 매 실행마다 새 세대를 생성한다. `(NEW,CATLG,DELETE)`는 "신규 생성 → 정상 종료 시 카탈로그 등록 → 비정상 종료 시 삭제"를 의미한다. Java로 치면 타임스탬프/시퀀스가 붙은 새 출력 파일을 매 실행마다 생성하는 것과 같다.

- **선행/후행 잡**:
  - **선행**: `POSTTRAN`(거래 포스팅, `CBTRN02C`) — 야간 배치 시퀀스상 이자 계산 전에 당일 거래가 먼저 포스팅되어 잔액이 확정되어야 한다.
  - **후행**: `TRANBKP`(거래 백업) → `COMBTRAN`(SORT로 거래 병합) → `TRANIDX`(AIX 재정의) → `OPENFIL`(CICS에 VSAM 재오픈).
  - 근거: 루트 README의 "Running Batch Jobs" 시퀀스 및 `scripts/run_full_batch.sh`의 순서(데이터 갱신 → POSTTRAN → INTCALC → TRANBKP → COMBTRAN → TRANIDX → OPENFIL). 이 잡 파일 자체에는 선후행 의존성이 명시되어 있지 않으므로 시퀀스는 프로젝트 문서 기준이다.

## Java/현대화 노트

- **PARM 일자 주입 → 잡 파라미터**: `PARM='2022071800'`은 Spring Batch의 `JobParameters`(예: `--run.date=20220718`) 또는 CLI 인자로 대체한다. 하드코딩 대신 스케줄러가 실행일자를 주입하도록 외부화해야 한다.

- **이자 산술 정밀도가 핵심**: COBOL `COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200`의 피연산자는 모두 고정소수점 packed decimal이다(`TRAN-CAT-BAL`/`WS-MONTHLY-INT`는 `S9(9)V99`, `DIS-INT-RATE`는 `S9(4)V99`, 예: 연율 12.50%). 반드시 `BigDecimal`(scale=2)로 매핑하고, 나눗셈에는 `RoundingMode`를 명시해야 한다. **COBOL `COMPUTE`의 기본 동작은 중간/최종 결과 절삭(truncate)**이므로, 부주의하게 `RoundingMode.HALF_UP`을 쓰면 1원 단위 차이가 누적된다. `double`/`float`는 절대 사용 금지(금전 계산 오차).

- **control-break → 그룹 집계/청크 경계**: 계정ID가 바뀔 때 직전 계정을 일괄 갱신하는 control-break 패턴은, Spring Batch에서 계정 단위 청크 경계 또는 `GROUP BY account_id` 집계로 표현한다. 핵심은 "정렬된 입력 스트림을 전제로 키 변화 시점에 집계 결과를 flush"한다는 점이다. 따라서 입력(`TCATBALF`)이 **계정ID로 정렬되어 있다는 전제**가 무너지면 결과가 틀린다 — Java 이행 시 이 정렬 보장을 명시적으로 처리해야 한다.

- **이자율 fallback 로직**: 프로그램은 `DISCGRP`에서 그룹별 이자율을 찾되, 없으면(FILE STATUS '23' = not found) 그룹ID `'DEFAULT'`로 재조회한다. Java에서는 `Optional` + 기본값 조회 폴백으로 옮기면 자연스럽다(예: `rates.getOrDefault(groupId, rates.get("DEFAULT"))`).

- **VSAM 다중 키 접근(`XREFFILE`/`XREFFIL1`)**: 동일 데이터셋을 베이스 클러스터(카드번호 키)와 AIX PATH(계정ID 키)로 이중 정의하는 패턴은 RDBMS의 보조 인덱스/별도 조회 메서드 두 개로 자연스럽게 풀린다. Java/JPA로 옮기면 단일 테이블에 두 인덱스를 두거나 두 개의 `findBy...` 쿼리로 대체된다.

- **출력 GDG → 버전드 출력 파일**: `SYSTRAN(+1)`의 세대 관리는 클라우드/Java 환경에서 타임스탬프·시퀀스가 붙은 출력 파일명 또는 파티셔닝된 출력 테이블로 대체한다. `(NEW,CATLG,DELETE)`의 "실패 시 삭제" 시맨틱은 트랜잭션 롤백/임시파일 정리 로직으로 보장해야 한다.

- **`SYSOUT`/`SYSPRINT` → 로깅**: 두 SYSOUT DD는 프로그램의 `DISPLAY` 및 런타임 메시지 출력 채널이다. Java에서는 SLF4J/Logback 등 표준 로깅으로 대체한다.

- **미구현 스텁 주의**: 잡 주석은 "interest and fees"를 계산한다고 하지만, `CBACT04C`의 수수료 계산 단락(`1400-COMPUTE-FEES`)은 `EXIT`만 있는 미구현 스텁이다. 즉 현재 이 잡은 **이자만** 계산한다. 마이그레이션 시 수수료 로직을 새로 구현할지 여부를 별도로 확인해야 한다.
