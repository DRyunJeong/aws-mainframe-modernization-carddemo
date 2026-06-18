# CLOSEFIL — CICS VSAM 파일 닫기 잡

- **유형**: JCL
- **한 줄 요약**: 야간 배치 처리 직전에 CICS 리전이 열어 두고 있는 VSAM 파일 5개를 CEMT 명령으로 강제 닫아 배치 프로그램이 배타적으로 접근할 수 있게 한다.

---

## 기능 설명

CICS 온라인 리전은 VSAM KSDS 파일들을 항상 열어 둔 채로 운영된다. 이 상태에서 배치 프로그램(예: POSTTRAN, INTCALC)이 동일 파일을 열려고 하면 VSAM의 공유 레벨 충돌로 인해 잡이 실패하거나 데이터가 오염될 수 있다.

CLOSEFIL 잡은 이 문제를 해결하기 위해 **SDSF 콘솔 커맨드 인터페이스**(`/F`)를 통해 CICS 리전(`CICSAWSA`)에 `CEMT SET FILE(...) CLO` 명령을 전달한다. CEMT(Customer Engineering Master Terminal)는 CICS 내장 관리 트랜잭션으로, 여기서는 배치 모드로 외부에서 구동된다.

닫혀진 파일 5개:
| CICS 파일명 | 대응 VSAM 데이터셋(추측) | 설명 |
|---|---|---|
| `TRANSACT` | `VSAM.TRANSACT` | 거래 KSDS |
| `CCXREF` | `VSAM.CARDXREF` | 카드↔계정 교차참조 KSDS |
| `ACCTDAT` | `VSAM.ACCTDAT` | 계정 KSDS |
| `CXACAIX` | `VSAM.CXACAIX` | 계정 AIX(Alternate Index) |
| `USRSEC` | `VSAM.USRSEC` | 사용자 보안 KSDS |

> **Java 관점**: CICS 파일 열기/닫기는 "데이터소스 연결 풀을 애플리케이션 서버가 독점적으로 관리하다가, 배치 전 풀을 반납"하는 것과 유사하다. 배타적 잠금이 필요한 배치 작업 전 `DataSource.close()` 혹은 `EntityManagerFactory.close()`를 호출하는 패턴에 해당한다.

---

## 스텝 구성

| 스텝명 | EXEC PGM / PROC | 역할 |
|---|---|---|
| `CLCIFIL` | `PGM=SDSF` | IBM SDSF(System Display and Search Facility)를 배치 모드로 실행. `ISFIN` DD에 작성된 `/F` 오퍼레이터 명령을 CICS 리전(`CICSAWSA`)으로 전달해 파일 5개를 순차적으로 닫는다. |

**ISFIN DD 내용 (라인 25–30)**:
```
/F CICSAWSA,'CEMT SET FIL(TRANSACT ) CLO'
/F CICSAWSA,'CEMT SET FIL(CCXREF  ) CLO'
/F CICSAWSA,'CEMT SET FIL(ACCTDAT ) CLO'
/F CICSAWSA,'CEMT SET FIL(CXACAIX ) CLO'
/F CICSAWSA,'CEMT SET FIL(USRSEC  ) CLO'
```
- `/F` = MVS MODIFY 콘솔 명령 (`F`orce modify)
- `CICSAWSA` = 대상 CICS 리전 잡명 (하드코딩)
- `CEMT SET FIL(...) CLO` = 지정 파일을 CLOSED 상태로 전환

**ISFOUT / CMDOUT DD**: `SYSOUT=*`로 SDSF 실행 로그와 명령 결과를 JES 스풀에 기록한다.

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 (인라인 JCL, PROC 미사용)

- **호출 프로그램 (EXEC PGM)**:
  - `SDSF` — IBM SDSF 배치 인터페이스 유틸리티. CICS 리전에 MVS MODIFY 명령을 전달하는 매개체. 별도 COBOL 프로그램 호출 없음.

- **데이터셋/파일/DB 테이블**:
  - CICS 파일 리소스 `TRANSACT`, `CCXREF`, `ACCTDAT`, `CXACAIX`, `USRSEC` — 실제 VSAM 데이터셋 이름은 CICS CSD(`app/csd/`) 정의에서 확인 가능 (추측: `AWS.M2.CARDDEMO.VSAM.*` 계열 HLQ).
  - JCL 자체에는 DD 문으로 데이터셋을 직접 참조하지 않음; 파일 접근은 CICS 내부에서 이뤄짐.

- **선행/후행 잡**:
  - **선행**: 없음 (배치 사이클의 최초 단계). CICS 리전이 반드시 기동 중이어야 한다.
  - **후행**: 마스터 파일 적재 잡들(`ACCTFILE`, `CARDFILE`, `XREFFILE`, `CUSTFILE`, `TRANFILE`, `DISCGRP`, `TCATBALF`, `TRANCATG`, `TRANTYPE` 등) → `POSTTRAN` → `INTCALC` → `TRANBKP` → `COMBTRAN` → `TRANIDX` → **`OPENFIL`** (CICS에 파일 재오픈).
  - 배치 사이클 전체 순서: `CLOSEFIL` → (파일 갱신 잡들) → `OPENFIL`. `scripts/run_full_batch.sh` 및 `scripts/remote_refresh.sh` 참조.

---

## Java/현대화 노트

### 1. SDSF 배치 모드 — Java 대응 없음

`PGM=SDSF`를 통한 CICS MODIFY 명령은 순수 메인프레임 운영 인프라다. Java 환경에서는 직접적인 대응이 없으며, 현대화 시 다음 패턴 중 하나로 대체된다:

- **Spring Batch JobListener**: `BeforeJobListener.beforeJob()`에서 데이터소스/파일 잠금 해제 로직 실행.
- **Kubernetes Job / Helm Hook**: 배치 파드 실행 전 `pre-job` 훅으로 온라인 서비스의 파일 핸들을 해제하는 어드민 API 호출.
- **Admin REST endpoint**: 온라인 서비스에 `/admin/files/close` API를 두고, 배치 잡 오케스트레이터(예: AWS Step Functions, Apache Airflow)가 배치 전 호출.

### 2. CICS 파일 공유 문제 — VSAM 특수성

VSAM KSDS는 CICS와 배치가 동시에 열면 `SHAREOPTIONS` 설정에 따라 충돌이 발생한다. 일반적으로 CICS는 `SHAREOPTIONS(2 3)` 또는 `(4 4)`로 정의하며, 배치가 `DISP=OLD`(배타)로 열기 위해서는 CICS 측이 먼저 닫아야 한다. Java/RDBMS 환경에서는 트랜잭션 격리 수준(Isolation Level)과 행 단위 잠금으로 이 문제를 회피하므로, 온라인·배치 동시 접근이 가능하다. VSAM에는 행 단위 잠금이 없으므로 파일 단위 닫기가 불가피하다.

### 3. CICS 리전명 하드코딩 (`CICSAWSA`)

`CICSAWSA`는 JCL에 하드코딩되어 있다(라인 26–30). 운영/개발 환경이 다르면 이 값을 변경해야 한다. 현대화 시에는 환경 변수나 파라미터화된 파이프라인 변수로 추출해야 한다.

### 4. 에러 처리 부재

CEMT 명령 실패(예: CICS 리전이 중지 상태, 파일명 오타) 시 SDSF는 스풀에 오류 메시지를 출력하지만 JCL RC(리턴코드)를 0이 아닌 값으로 설정하지 않을 수 있다 (추측). 결과적으로 후속 배치 잡이 파일이 열린 채로 실행되어 VSAM 충돌이 발생할 수 있다. 현대화 시 반드시 실패 감지 및 파이프라인 중단 로직이 필요하다.

### 5. 배치 사이클에서의 위치

```
[온라인 CICS 운영 중]
      │
      ▼
CLOSEFIL  ← 이 잡 (CICS에 VSAM 파일 닫기 지시)
      │
      ▼
파일 적재/갱신 잡들 (배타적 VSAM 접근)
      │
      ▼
POSTTRAN → INTCALC → TRANBKP → COMBTRAN → TRANIDX
      │
      ▼
OPENFIL   (CICS에 VSAM 파일 재오픈 지시)
      │
      ▼
[온라인 CICS 재개]
```

---

*소스: `app/jcl/CLOSEFIL.jcl` — CardDemo v1.0-15-g27d6c6f-68 (2022-07-19)*
