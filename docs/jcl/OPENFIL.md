# OPENFIL — CICS VSAM 파일 재오픈 잡

- **유형**: JCL
- **한 줄 요약**: 야간 배치 처리가 완료된 후, 닫혀 있던 CICS VSAM 파일들을 CEMT 명령으로 다시 열어 온라인 서비스를 복구한다.

---

## 기능 설명

OPENFIL은 야간 배치 사이클(POSTTRAN → INTCALC → TRANBKP → COMBTRAN → TRANIDX)이 모두 끝난 뒤 **마지막 단계**로 실행되는 잡이다. 배치 잡들은 VSAM KSDS 파일에 직접 접근하기 위해 사전에 CICS가 파일을 해제(CLOSE)한 상태에서 실행된다. 모든 배치 처리가 완료되면 OPENFIL이 SDSF를 통해 운영 CICS 리전(`CICSAWSA`)에 `CEMT SET FILE(...) OPEN` 명령을 전달하여 각 VSAM 파일을 다시 열고, CICS 온라인 트랜잭션이 해당 파일에 다시 접근할 수 있게 한다.

Java/현대 시스템 관점에서는 **데이터베이스 커넥션 풀 재개(resume)** 또는 **마이크로서비스 readiness probe 복구** 절차에 해당한다. 배치 처리 중에는 운영 서비스가 파일에 접근하지 못하게 막았다가(CLOSEFIL 잡이 선행), 배치가 끝나면 다시 서비스 가능 상태로 전환하는 패턴이다.

---

## 스텝 구성

| 스텝명    | EXEC PGM/PROC | 역할                                                                                          |
|-----------|---------------|-----------------------------------------------------------------------------------------------|
| `OPCIFIL` | `PGM=SDSF`    | IBM SDSF(System Display and Search Facility)를 일괄 모드로 실행하여 ISFIN DD로 공급된 CEMT 명령을 CICS 리전에 전달. TRANSACT·CCXREF·ACCTDAT·CXACAIX·USRSEC 5개 파일을 순서대로 OPEN한다. |

> **SDSF 배치 모드 설명**: SDSF는 일반적으로 TSO 대화형 패널 도구이지만, `ISFIN DD *`에 `/F jobname,'operator command'` 형식으로 연산자 명령(Operator Command)을 입력하면 JES에 콘솔 명령을 전달하는 배치 드라이버로도 동작한다. `/F CICSAWSA,'CEMT ...'`는 `MODIFY CICSAWSA,...` 연산자 명령의 단축 표기로, 해당 CICS 리전 작업에 CEMT 마스터 터미널 명령을 인라인으로 주입한다. ISFOUT/CMDOUT는 명령 처리 결과를 SYSOUT으로 내보낸다.

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 — 단일 인라인 스텝이며 PROC 참조 없음.

- **호출 프로그램 (EXEC PGM)**:
  - `SDSF` — IBM 제공 시스템 유틸리티. CICS 리전에 연산자 명령을 전달하기 위해 사용. 일반 애플리케이션 프로그램이 아니므로 별도 컴파일 불필요.

- **데이터셋/파일/DB 테이블**:
  - `TRANSACT` — 거래(Transaction) VSAM KSDS. CICS 파일 명칭. 대응 copybook: `app/cpy/CVTRA05Y.cpy`(추측 — CICS CSD의 FILE 정의와 매핑).
  - `CCXREF` — 카드↔계정 교차 참조(Card/Account Cross-Reference) VSAM KSDS. 대응 copybook: `app/cpy/CVACT03Y.cpy`.
  - `ACCTDAT` — 계정 데이터(Account Data) VSAM KSDS. 대응 copybook: `app/cpy/CVACT01Y.cpy`.
  - `CXACAIX` — 계정 데이터의 대체 인덱스(Alternate Index, AIX). `ACCTDAT`의 보조 경로(Path)로 정의됨(추측 — CICS CSD에 PATH 자원으로 등록).
  - `USRSEC` — 사용자 보안(User Security) VSAM KSDS. 대응 copybook: `app/cpy/CSUSR01Y.cpy`.
  - `ISFOUT`, `CMDOUT` — SYSOUT 스풀. 명령 응답 로그. 영구 데이터셋 아님.

- **선행/후행 잡**:
  - **선행**: `CLOSEFIL` 잡 (배치 사이클 시작 전 CICS에서 동일 파일들을 CLOSE) → `POSTTRAN`(CBTRN02C 거래 전기) → `INTCALC`(CBACT04C 이자 계산) → `TRANBKP`(거래 백업) → `COMBTRAN`(SORT 병합) → `TRANIDX`(대체 인덱스 재구성).
  - **후행**: 없음 — OPENFIL은 야간 배치 사이클의 **최종 잡**. 완료 후 CICS 온라인 트랜잭션이 파일에 즉시 접근 가능해진다.

---

## Java/현대화 노트

### 1. SDSF 배치 모드 → Java 대응 없음, 운영 자동화 도구로 대체

SDSF를 통한 `CEMT SET FILE OPEN`은 **CICS 전용 관리 프로토콜**로, Java 세계에 직접 대응하는 API가 없다. 현대화 시 다음 대안을 고려한다.

```java
// 현대화 대응 예시 1 — Spring Batch JobExecutionListener로 배치 후 처리
@Component
public class CicsFileReopenListener implements JobExecutionListener {
    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            // CICS REST API 또는 CMCI(CICS Management Client Interface)로 파일 재오픈
            cicsManagementService.openFile("TRANSACT");
            cicsManagementService.openFile("CCXREF");
            cicsManagementService.openFile("ACCTDAT");
            cicsManagementService.openFile("CXACAIX");
            cicsManagementService.openFile("USRSEC");
        }
    }
}
```

```java
// 현대화 대응 예시 2 — CMCI REST API(IBM CICS TS 5.1+) 호출
// PUT https://<cmci-host>:<port>/CICSSystemManagement/CICSFile/<region>/TRANSACT
// body: {"OPENSTATUS": "OPEN"}
RestTemplate rest = new RestTemplate();
rest.put(cmciUrl + "/CICSFile/CICSAWSA/TRANSACT",
         Map.of("OPENSTATUS", "OPEN"));
```

완전 마이그레이션 시나리오(VSAM → RDB)에서는 이 잡 자체가 불필요해진다: RDBMS는 배치와 온라인이 동일한 테이블에 동시 접근하므로 CLOSE/OPEN 사이클이 없다.

### 2. CICS 파일 이름 ↔ VSAM 데이터셋 이름 분리

JCL에 등장하는 `TRANSACT`, `CCXREF`, `ACCTDAT`, `CXACAIX`, `USRSEC`는 **CICS 파일 자원 이름**(CSD FILE 정의의 `FILE(...)`)이며, 실제 VSAM 데이터셋 이름(DSN)은 CSD의 `DSNAME(...)` 속성으로 별도 지정된다. COBOL 배치 프로그램의 `SELECT ... ASSIGN TO` DD명과는 다른 이름 공간임을 주의해야 한다.

| CICS 파일명 | 대응 COBOL 배치 DD명(추측) | 역할 |
|-------------|---------------------------|------|
| `TRANSACT`  | `TRANSACT`               | 거래 KSDS |
| `CCXREF`    | `CARDXREF`               | 카드/계정 교차참조 KSDS |
| `ACCTDAT`   | `ACCTDATA`               | 계정 KSDS |
| `CXACAIX`   | (AIX path)               | 계정 대체 인덱스 |
| `USRSEC`    | `USRSECID`(추측)         | 사용자 보안 KSDS |

### 3. 대체 인덱스(AIX) `CXACAIX`

`CXACAIX`는 독립 파일이 아니라 `ACCTDAT`의 AIX(Alternate Index) 경로다. Java/JPA에서 `@Index`가 같은 엔티티 테이블에 별도 인덱스 경로를 제공하는 것과 동일한 개념이다. AIX도 CICS에 별도 FILE 자원으로 등록되므로 OPEN 명령이 개별적으로 필요하다.

### 4. 배치-온라인 파일 경합(File Contention) 문제

VSAM KSDS는 CICS가 파일을 열고 있는 동안 배치 프로그램이 동시에 쓸 수 없다(ENQ/DEQ 직렬화). 이 때문에 CLOSEFIL/OPENFIL 패턴이 존재한다. Java 기반 현대화에서 VSAM을 그대로 유지하는 경우에도 이 직렬화 제약은 동일하게 존재하므로, 배치 윈도우(batch window) 동안 CICS 파일을 닫는 절차를 운영 자동화 도구(Control-M, AWS Step Functions 등)로 재구현해야 한다.

### 5. 오류 처리 부재

현재 JCL에는 SDSF 명령 실패 시 알림이나 조건 코드(COND) 처리가 없다. 파일 오픈이 일부만 성공해도 잡은 RC=0으로 정상 종료될 수 있다(추측). ISFOUT/CMDOUT 스풀 로그를 확인하거나, CMCI를 사용하는 경우 HTTP 응답 코드로 성공/실패를 판별하도록 설계해야 한다.
