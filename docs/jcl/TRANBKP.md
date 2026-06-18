# TRANBKP — 거래 마스터 백업 및 VSAM 재초기화 잡

- **유형**: JCL (배치 잡)
- **한 줄 요약**: 처리 완료된 거래 VSAM KSDS를 GDG 백업 파일로 복사한 뒤, 기존 VSAM 클러스터(본체 + AIX)를 삭제하고 빈 상태로 재정의하여 다음 배치 사이클을 위한 공간을 확보한다.

---

## 기능 설명

TRANBKP는 야간 배치 파이프라인(INTCALC 직후, COMBTRAN 직전)에서 실행되는 **VSAM 백업 및 재초기화** 잡이다.

핵심 목적은 두 가지다.

1. **데이터 보존**: IDCAMS REPRO 명령으로 현재 거래 KSDS(`AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS`)의 전체 내용을 세대 데이터 그룹(GDG) 백업 데이터셋(`AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)`)에 순차 복사한다. GDG의 `(+1)` 표기는 "새 세대 생성"을 의미하며, 최대 5세대가 유지된다(DEFGDGB.jcl 기준, L29: `LIMIT(5)`).

2. **VSAM 재초기화**: IDCAMS DELETE로 클러스터와 AIX를 완전 삭제한 후, IDCAMS DEFINE CLUSTER로 동일한 이름·파라미터로 새 빈 KSDS를 생성한다. 이로써 후속 잡 COMBTRAN이 정렬된 결합 거래 파일을 REPRO로 VSAM에 적재할 수 있는 빈 공간이 준비된다.

VSAM 파일은 Java의 `HashMap`/`TreeMap`처럼 키-값 랜덤 접근이 가능한 인덱스 파일이다. KSDS(Key-Sequenced Data Set)는 레코드를 키 순서로 저장하고 기본 키 인덱스를 자동 관리한다. Java 관점에서 이 잡은 다음과 동일한 작업이다.

```java
// 1단계: 현재 DB 테이블 내용을 백업 테이블로 복사
INSERT INTO TRANSACT_BKUP_GEN_N SELECT * FROM TRANSACT_VSAM_KSDS;

// 2단계: 원본 테이블 DROP 후 빈 테이블로 재생성
DROP TABLE TRANSACT_VSAM_KSDS;
CREATE TABLE TRANSACT_VSAM_KSDS (TRAN_ID CHAR(16) PRIMARY KEY, ...);
```

---

## 스텝 구성

| 스텝명    | EXEC PGM / PROC         | 역할                                                                                         |
|-----------|-------------------------|----------------------------------------------------------------------------------------------|
| `STEP05R` | `PROC=REPROC`           | REPROC 프로시저 호출. 내부에서 PGM=IDCAMS를 실행하며 SYSIN에 `REPRO INFILE(FILEIN) OUTFILE(FILEOUT)` 명령을 사용(app/ctl/REPROCT.ctl). `PRC001.FILEIN`을 VSAM KSDS, `PRC001.FILEOUT`을 GDG 백업(+1)으로 오버라이드한다. |
| `STEP05`  | `PGM=IDCAMS`            | VSAM 클러스터 삭제. `DELETE ... CLUSTER` 및 `DELETE ... ALTERNATEINDEX` 두 명령을 순서대로 실행한다. `IF MAXCC LE 08 THEN SET MAXCC = 0`으로 파일이 존재하지 않을 때 발생하는 RC=8을 정상으로 처리(멱등성 보장). |
| `STEP10`  | `PGM=IDCAMS, COND=(4,LT)` | VSAM 클러스터 재정의. STEP05의 RC가 4 이상이면(즉 RC >= 4이면 COND=(4,LT) 조건이 참이 되어 스텝을 건너뜀 — 단, STEP05에서 RC를 0으로 리셋하므로 정상 흐름에서는 항상 실행됨). `CYLINDERS(1 5)`, `KEYS(16 0)`, `RECORDSIZE(350 350)` 파라미터로 새 KSDS를 정의한다. |

### REPROC 프로시저 내부 구조 (app/proc/REPROC.prc)

```
//PRC001 EXEC PGM=IDCAMS
//FILEIN  DD DSN=NULLFILE   ← JCL에서 PRC001.FILEIN으로 오버라이드
//FILEOUT DD DSN=NULLFILE   ← JCL에서 PRC001.FILEOUT으로 오버라이드
//SYSIN   DD DSN=&CNTLLIB(REPROCT)   ← app/ctl/REPROCT.ctl: REPRO INFILE(FILEIN) OUTFILE(FILEOUT)
```

프로시저의 DD 오버라이드는 Java 메서드의 매개변수 전달과 유사하다. 프로시저는 "함수 템플릿"이고, JCL의 `PRC001.FILEIN DD ...`는 그 매개변수를 실제 값으로 치환하는 것이다.

---

## 의존성

- **COPY (PROC/INCLUDE)**:
  - `PROC=REPROC` — `app/proc/REPROC.prc`. IDCAMS REPRO 유틸리티를 래핑하는 범용 프로시저. `CNTLLIB=AWS.M2.CARDDEMO.CNTL` 파라미터를 받아 SYSIN 제어문 PDS를 지정한다.
  - JCLLIB: `AWS.M2.CARDDEMO.PROC` — REPROC.prc가 위치하는 프로시저 라이브러리 (JCL L19).
  - SYSIN 제어문: `AWS.M2.CARDDEMO.CNTL(REPROCT)` → `app/ctl/REPROCT.ctl` 내용: `REPRO INFILE(FILEIN) OUTFILE(FILEOUT)`.

- **호출 프로그램 (EXEC PGM)**:
  - `IDCAMS` — IBM 제공 액세스 메서드 서비스 유틸리티. Java 표준 라이브러리에는 직접 대응물이 없으며, VSAM 파일 생성/삭제/복사/목록 조회 등 VSAM 파일 시스템 전반을 관장하는 OS 수준 유틸리티다. AWS Mainframe Modernization(M2) 환경에서는 대응하는 파일 마이그레이션 도구가 이 역할을 대신한다.

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` (입력/재정의 대상) — 거래 마스터 VSAM KSDS. 키 길이 16바이트(오프셋 0), 레코드 길이 350바이트 고정. 기본 키는 TRAN-ID. STEP05R에서 읽히고, STEP05에서 삭제되며, STEP10에서 빈 상태로 재생성된다.
  - `AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX` (삭제 대상) — 거래 KSDS의 보조 인덱스(Alternate Index). STEP05에서 함께 삭제된다. AIX는 기본 키 외 다른 필드로도 레코드를 검색할 수 있게 하는 보조 인덱스로, Java의 `Map` 키가 여러 개인 것과 유사하다. STEP10 DEFINE CLUSTER에는 AIX 재정의가 없으므로, AIX 재생성은 후속 잡 `TRANIDX`가 담당한다(추측 — TRANIDX.jcl 확인 필요).
  - `AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)` (출력, 신규 생성) — GDG(Generation Data Group) 백업 파일. `(+1)`은 새 세대를 의미하며 GDG 베이스는 DEFGDGB.jcl에서 `LIMIT(5) SCRATCH`로 정의되어 있어 최대 5세대까지 보관한다. DCB: `LRECL=350, RECFM=FB` — 350바이트 고정 길이 순차 파일. 사이클 완료 후 COMBTRAN이 `(0)`(현재 세대)으로 이 파일을 읽는다.
  - `AWS.M2.CARDDEMO.CNTL(REPROCT)` (SYSIN 제어문 PDS 멤버) — REPROC 프로시저가 사용하는 IDCAMS REPRO 명령문 소스.
  - VOLUME: `AWSHJ1` — STEP10의 DEFINE CLUSTER에서 지정된 DASD 볼륨 시리얼. 실제 환경에 맞게 변경이 필요하다.

- **선행/후행 잡**:
  - 선행: `INTCALC` — CBACT04C가 이자 거래 레코드를 `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS`에 기록한 후 TRANBKP가 실행된다. INTCALC 완료 전에 TRANBKP가 실행되면 이자 거래가 백업에 포함되지 않고 VSAM에서도 사라진다.
  - 후행: `COMBTRAN` — TRANBKP가 생성한 `AWS.M2.CARDDEMO.TRANSACT.BKUP(0)`(방금 생성된 세대 = 현재 세대)를 `AWS.M2.CARDDEMO.SYSTRAN(0)`과 함께 SORT로 합산하여 `AWS.M2.CARDDEMO.TRANSACT.COMBINED(+1)`을 만들고, 이를 재생성된 빈 KSDS에 REPRO로 적재한다.
  - 후행: `TRANIDX` — 빈 상태로 재생성된 KSDS에 대해 AIX(보조 인덱스)를 재정의·재빌드한다(추측 — TRANIDX.jcl 확인 권장).
  - 참고 순서: `CLOSEFIL → 마스터갱신 → POSTTRAN → INTCALC → **TRANBKP** → COMBTRAN → TRANIDX → OPENFIL`

---

## Java/현대화 노트

### 1. GDG (Generation Data Group) → 버전 관리된 스냅샷 테이블

GDG는 같은 이름으로 여러 세대를 보관하는 mainframe의 파일 버전 관리 메커니즘이다. `(+1)`은 새 세대 생성, `(0)`은 현재(가장 최근) 세대, `(-1)`은 직전 세대를 가리킨다. Java/현대 스택에서의 대응은 다음과 같다.

```java
// GDG 유사 패턴 — 테이블에 세대 컬럼 추가
INSERT INTO TRANSACT_BKUP (generation, tran_id, ...)
SELECT MAX(generation) + 1, tran_id, ...
FROM TRANSACT_VSAM_KSDS;

// 또는 S3 버전 관리(versioned object storage) 활용
s3Client.putObject(PutObjectRequest.builder()
    .bucket("carddemo-backup")
    .key("TRANSACT/BKUP/latest")
    .build(), ...);
```

Spring Batch에서는 `JobExecutionId`나 실행 타임스탬프를 파티션 키로 사용하여 동일한 효과를 낼 수 있다.

### 2. COND 파라미터의 반직관적 의미

```
COND=(4,LT)
```

이 조건은 "이전 스텝의 RC가 4 미만(LT)이면 이 스텝을 **건너뛴다**"는 뜻이 **아니라**, "4 < 이전 RC이면 건너뛴다"로 읽힌다. 즉 `4 LT 이전RC` → "4가 이전 RC보다 작으면(= 이전 RC > 4이면) 이 스텝을 BYPASS한다". Java의 조건문으로 표현하면 다음과 같다.

```java
if (previousReturnCode > 4) {
    skipStep10(); // COND=(4,LT) 조건이 참 → 스텝 실행 안 함
} else {
    executeStep10(); // 정상 실행
}
```

STEP05에서 `IF MAXCC LE 08 THEN SET MAXCC = 0`으로 RC를 0으로 리셋하므로, 정상 흐름에서 STEP10은 항상 실행된다.

### 3. IDCAMS DELETE의 멱등성 처리

```
DELETE AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS CLUSTER
IF MAXCC LE 08 THEN SET MAXCC = 0
```

파일이 없을 때 IDCAMS DELETE는 RC=8을 반환한다. `IF MAXCC LE 08 THEN SET MAXCC = 0`은 이를 정상으로 처리하여 잡이 ABEND되지 않도록 한다. Java/Spring Batch에서는 `@ConditionalOnMissingBean`이나 `try { drop } catch (TableNotExistsException e) { /* ignore */ }` 패턴에 해당한다.

### 4. VSAM KSDS → 현대화 대응

| VSAM 개념           | Java/현대 대응                                          |
|---------------------|--------------------------------------------------------|
| KSDS 클러스터       | 기본 키 인덱스가 있는 RDB 테이블 또는 DynamoDB 테이블    |
| AIX (보조 인덱스)   | RDB의 Secondary Index / Elasticsearch 보조 쿼리 인덱스  |
| LRECL=350, RECFM=FB | 고정 길이 바이트 배열 → Java 레코드 클래스 (350 필드)    |
| VOLUMES(AWSHJ1)     | 스토리지 볼륨 → AWS EBS/S3 버킷 (추상화됨)              |
| GDG (BKUP(+1))      | S3 버전 관리 또는 타임스탬프 파티셔닝된 테이블           |

### 5. SHAREOPTIONS(2 3) 의미

STEP10 DEFINE CLUSTER에서 지정된 `SHAREOPTIONS(2 3)`는 다음을 의미한다.
- `2` (크로스 리전 공유): 같은 시스템 내에서 여러 잡이 동시에 읽을 수 있고, 쓰기 시 독점 접근 보장.
- `3` (크로스 시스템): 여러 시스템에서 동시에 읽기/쓰기 가능(무결성은 애플리케이션 책임).

Java 관점에서는 `ReadWriteLock`의 비교적 느슨한 버전에 해당한다. 야간 배치처럼 CICS가 파일을 닫은 상태(CLOSEFIL 후)에서 실행되므로 실제 충돌 위험은 낮다.

### 6. 마이그레이션 시 주의 사항

- **레코드 길이 350바이트**: `RECORDSIZE(350 350)` — 최소/최대 모두 350으로 고정 길이 레코드임을 확인. Java 마이그레이션 시 레코드를 `byte[350]`으로 읽어 `ByteBuffer`로 파싱하거나, copybook `CVTRA0*Y` 구조에 맞는 DTO 클래스로 역직렬화해야 한다.
- **키 오프셋 0, 길이 16**: `KEYS(16 0)` — TRAN-ID(PIC X(16))가 레코드 맨 앞 16바이트. RDB 마이그레이션 시 VARCHAR(16) PRIMARY KEY로 매핑.
- **AIX 재생성 순서**: TRANBKP는 AIX를 삭제하지만 재생성하지 않는다. COMBTRAN이 REPRO로 KSDS에 데이터를 적재한 후 TRANIDX가 AIX를 재빌드해야 한다. 이 순서가 어긋나면 AIX를 통한 조회가 실패한다.
- **GDG 세대 참조**: COMBTRAN이 `BKUP(0)`을 읽는다. TRANBKP 실행 직후에는 `BKUP(+1)`로 생성된 파일이 현재 세대 `(0)`이 된다. GDG 세대 롤링 로직을 현대화할 때는 이 암묵적인 세대 전환을 명시적인 버전 컬럼이나 타임스탬프로 대체해야 한다.
