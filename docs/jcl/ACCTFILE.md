# ACCTFILE — 계정 마스터 VSAM 파일 삭제·정의·적재 잡

- **유형**: JCL (배치 잡, IDCAMS 유틸리티 전용)
- **한 줄 요약**: 계정 마스터 KSDS(`AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS`)를 기존 클러스터 삭제 → 재정의 → 평문(PS) 소스 파일에서 REPRO로 초기 적재하는 3스텝 리프레시 잡.

---

## 기능 설명

이 잡은 CardDemo 시스템의 계정 마스터 VSAM KSDS를 완전히 새로 구축한다.
실행 순서는 다음과 같다.

1. 혹시 남아 있는 기존 KSDS 클러스터를 조건부로 삭제해 깨끗한 슬레이트를 만든다.
2. 새 KSDS 클러스터(데이터 컴포넌트 + 인덱스 컴포넌트)를 정의한다.
3. 순차 평문 파일(PS)에 담긴 초기 데이터를 REPRO로 KSDS에 복사해 적재를 완성한다.

배치 메모리의 야간 시퀀스 기록에 따르면 이 잡(`ACCTFILE`)은 `POSTTRAN`(CBTRN02C)이나 `INTCALC`(CBACT04C) 실행 **전**에 마스터 파일 갱신 단계로 수행된다.
운영 환경에서는 데이터 마이그레이션, 재해복구 리스토어, 개발·테스트 환경 초기화 등의 목적으로 실행된다.

> **주의**: 이 잡은 멱등(idempotent) 설계다. STEP05의 `IF MAXCC LE 08 THEN SET MAXCC = 0`(25–27행) 덕분에 클러스터가 없어 DELETE가 실패(CC=8)해도 잡은 중단되지 않는다.

---

## 스텝 구성

| 스텝명  | EXEC PGM / PROC | 역할 |
|---------|-----------------|------|
| STEP05  | `PGM=IDCAMS`    | 기존 KSDS 클러스터 조건부 삭제. `DELETE ... CLUSTER` 후 `IF MAXCC LE 08 THEN SET MAXCC = 0`으로 "파일 없음(CC=8)" 오류를 무시하고 잡을 계속 진행시킨다. |
| STEP10  | `PGM=IDCAMS`    | 새 KSDS 클러스터 정의. 키 길이 11바이트(오프셋 0), 레코드 크기 고정 300바이트, 볼륨 `AWSHJ1`, 1차 1실린더·2차 5실린더 할당. DATA·INDEX 컴포넌트를 명시적으로 별도 이름으로 생성한다. |
| STEP15  | `PGM=IDCAMS`    | 순차 평문 파일(`ACCTDATA` DD → PS)을 VSAM KSDS(`ACCTVSAM` DD)에 REPRO(레코드 단위 복사)로 적재한다. |

---

## 의존성

### COPY (PROC/INCLUDE)
없음. 이 잡은 외부 PROC이나 INCLUDE를 참조하지 않는다. 모든 SYSIN 제어문이 잡 내부에 인라인으로 포함되어 있다.

### 호출 프로그램 (EXEC PGM)
- `IDCAMS` — IBM Access Method Services 유틸리티. VSAM 클러스터 관리(DELETE, DEFINE CLUSTER)와 레코드 복사(REPRO)를 모두 담당한다. 세 스텝 모두 동일한 유틸리티를 호출한다.

### 데이터셋/파일/DB 테이블

| 데이터셋 이름 | DISP / 유형 | 방향 | 설명 |
|---|---|---|---|
| `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | VSAM KSDS | 대상(삭제→생성) | 계정 마스터 KSDS 클러스터 본체. 키 11바이트(오프셋 0), 고정 300바이트 레코드. |
| `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.DATA` | VSAM DATA 컴포넌트 | 대상(생성) | KSDS의 데이터 익스텐트. |
| `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.INDEX` | VSAM INDEX 컴포넌트 | 대상(생성) | KSDS의 인덱스 익스텐트. |
| `AWS.M2.CARDDEMO.ACCTDATA.PS` | `DISP=SHR`, 순차(PS) | 입력 | REPRO 원본 소스. 고정 300바이트 레코드가 담긴 평문 순차 파일. 이 파일은 잡이 미리 존재한다고 가정한다. |

볼륨 레이블: `AWSHJ1` (STEP10 DEFINE 39행). 실제 볼륨 이름은 설치 환경마다 다를 수 있다 (추측: 개발용 고정 볼륨명).

SHAREOPTIONS(2 3): 동시 접근 정책 — 같은 시스템에서 다중 사용자 읽기(레벨 2), 다른 시스템과 비독점 공유(레벨 3). CICS와 배치가 번갈아 파일을 사용하는 CardDemo 구조에 맞는 설정이다.

ERASE 옵션: 클러스터 삭제 시 디스크 블록을 0x00으로 덮어 써 데이터를 완전히 소거한다.

### 선행/후행 잡

| 구분 | 잡 이름 | 설명 |
|------|---------|------|
| **선행** | (없음 또는 운영 스케줄러 지정) | `AWS.M2.CARDDEMO.ACCTDATA.PS` 소스 파일이 먼저 준비되어 있어야 한다. 데이터 익스포트 JCL이나 FTP 수신 잡이 선행될 수 있다. |
| **후행** | `POSTTRAN` (CBTRN02C) | 일일 거래 전기 배치. `ACCTDATA` KSDS를 I-O 모드로 열어 계정 잔액을 갱신한다. |
| **후행** | `INTCALC` (CBACT04C) | 이자 계산 배치. `ACCTFILE` DD로 동일 KSDS를 I-O 접근한다. |
| **후행** | `OPENFIL` | CICS가 닫아둔 VSAM 파일을 다시 여는 잡. ACCTFILE 완료 후 CICS 온라인 트랜잭션이 파일에 접근할 수 있도록 한다. |

---

## Java / 현대화 노트

### 개념 대응

| JCL / IDCAMS 개념 | Java / 현대 개념 |
|---|---|
| `DEFINE CLUSTER ... KSDS` | 테이블 DDL(`CREATE TABLE`)과 인덱스 DDL(`CREATE INDEX`)을 합친 것 |
| `DELETE CLUSTER` + `IF MAXCC LE 08 SET MAXCC = 0` | `DROP TABLE IF EXISTS` (SQL) 또는 `deleteIfExists()` (Files API) |
| `REPRO INFILE(...) OUTFILE(...)` | `INSERT INTO target SELECT * FROM source` 또는 ETL 도구의 bulk load 단계 |
| PS(평문 순차 파일) | CSV / 고정 너비 텍스트 파일 또는 S3 오브젝트 |
| KSDS(키 순차 데이터셋) | 기본 키 인덱스가 있는 관계형 테이블, 또는 키-값 스토어(DynamoDB 등) |

### 마이그레이션 포인트

1. **레코드 크기 고정 300바이트**: KSDS는 가변 레코드를 지원하지만 이 클러스터는 `RECORDSIZE(300 300)`으로 최소=최대=300바이트 고정이다. Java로 마이그레이션 시 읽기/쓰기 코드에서 반드시 300바이트 패딩/트리밍 로직이 필요하다. `CBACCT01Y` 또는 관련 copybook이 실제 필드 레이아웃을 정의한다(copybook을 별도로 확인할 것).

2. **키 정의 `KEYS(11 0)`**: 레코드의 바이트 오프셋 0에서 시작하는 11바이트가 KSDS의 기본 키다. Java 엔티티에서는 이 필드가 `@Id`에 해당한다. 키 값이 EBCDIC 문자열이라면 ASCII 변환 후 공백 트리밍이 필요하다.

3. **IDCAMS REPRO의 EBCDIC 문제**: 메인프레임의 PS 파일은 EBCDIC 인코딩이다. AWS Mainframe Modernization 환경이나 Linux/Java 환경으로 마이그레이션 시 소스 파일(`ACCTDATA.PS`)을 먼저 ASCII(또는 UTF-8)로 변환하거나, Java 로더에서 `Charset.forName("IBM037")` 등으로 디코딩해야 한다.

4. **SHAREOPTIONS(2 3)과 동시성**: CICS 온라인과 배치가 같은 파일을 공유하는 메인프레임 패턴은 Java 환경에서는 데이터베이스 트랜잭션 격리 수준과 커넥션 풀 설계로 대체된다. 동일 테이블에 온라인 API와 배치 잡이 동시에 접근한다면 낙관적 잠금(`@Version`)이나 배치 실행 시간대 분리를 고려해야 한다.

5. **멱등 삭제 패턴**: `IF MAXCC LE 08 THEN SET MAXCC = 0`은 JCL의 고전적인 "오류 무시" 패턴이다. Spring Batch나 AWS Glue 잡으로 구현할 때는 `StepBuilder.allowStartIfComplete(true)` 또는 테이블 존재 여부 사전 체크로 동일한 멱등성을 구현한다.

6. **볼륨 `AWSHJ1` 하드코딩**: 실제 배포 환경에서는 볼륨 이름이 달라진다. AWS Mainframe Modernization(M2)이나 Blu Age 변환 시 이 파라미터는 S3 버킷 경로나 EFS 마운트 포인트로 대체된다.
