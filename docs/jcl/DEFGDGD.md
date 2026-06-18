# DEFGDGD — DB2 참조 데이터 GDG 베이스 정의 및 최초 세대 적재 잡

- **유형**: JCL
- **한 줄 요약**: 거래유형(TRANTYPE), 거래카테고리(TRANCATG), 공시그룹(DISCGRP) 세 참조 파일에 대해 GDG(Generation Data Group) 베이스를 IDCAMS로 정의하고, IEBGENER로 현행 PS 파일을 첫 번째 세대(`(+1)`)로 복사·등록한다.

---

## 기능 설명

GDG(Generation Data Group)는 IBM 메인프레임에서 동일한 논리적 이름 아래 버전화(versioned)된 순차 데이터셋 시리즈를 관리하는 기구로, Java 애플리케이션의 **롤링 백업 디렉토리** 또는 **버전 관리된 S3 객체 버킷**에 해당한다.

이 잡은 **초기 설치 시 1회 실행**하는 준비 잡이다. 수행 흐름은 두 단계 쌍으로 반복된다:

1. **DEFINE 단계(IDCAMS)**: SMS 카탈로그에 GDG 베이스 엔트리를 생성한다. `LIMIT(5)`는 최대 5개의 세대를 유지하고, `SCRATCH`는 세대가 만료(LIMIT 초과)될 때 자동으로 삭제(uncatalog + scratch)하도록 지시한다.
2. **COPY 단계(IEBGENER)**: 현재 운영 중인 순차 파일(PS)을 GDG의 `(+1)` 세대, 즉 새로운 최신 세대로 복사한다. `DISP=(NEW,CATLG)`는 신규 할당 후 카탈로그에 등록한다는 뜻이다.

이 패턴을 세 개의 참조 데이터셋에 반복 적용하므로 총 6개의 스텝으로 구성된다.

> **주의**: STEP30이 JOB 카드 주석에 `RESTART=STEP30`으로 명시되어 있다(1번 라인). 이는 STEP20까지는 성공했지만 STEP30에서 실패했을 때 해당 스텝부터 재실행할 수 있음을 운영자에게 알려 주는 힌트다.

---

## 스텝 구성

| 스텝명 | EXEC PGM | 역할 |
|--------|----------|------|
| STEP10 | `IDCAMS` | `AWS.M2.CARDDEMO.TRANTYPE.BKUP` GDG 베이스 정의 (`LIMIT(5) SCRATCH`) |
| STEP20 | `IEBGENER` | `TRANTYPE.PS` → `TRANTYPE.BKUP(+1)` 첫 세대 복사, LRECL=60 FB |
| STEP30 | `IDCAMS` | `AWS.M2.CARDDEMO.TRANCATG.PS.BKUP` GDG 베이스 정의 (`LIMIT(5) SCRATCH`); `COND=(0,NE)` |
| STEP40 | `IEBGENER` | `TRANCATG.PS` → `TRANCATG.PS.BKUP(+1)` 첫 세대 복사, LRECL=60 FB; `COND=(0,NE)` |
| STEP50 | `IDCAMS` | `AWS.M2.CARDDEMO.DISCGRP.BKUP` GDG 베이스 정의 (`LIMIT(5) SCRATCH`) |
| STEP60 | `IEBGENER` | `DISCGRP.PS` → `DISCGRP.BKUP(+1)` 첫 세대 복사, LRECL=50 FB; `COND=(0,NE)` |

**COND=(0,NE)** 해설: "이전 스텝의 리턴 코드가 0이 아니면 이 스텝을 건너뛴다"는 뜻이다. Java로 비유하면 다음과 같다:

```java
if (previousStepReturnCode != 0) {
    skipThisStep();
}
```

STEP10(IDCAMS)에는 COND가 없으므로 항상 실행된다. STEP20부터는 앞 스텝이 성공(RC=0)해야만 실행된다.

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 (인라인 JCL 전용)

- **호출 프로그램 (EXEC PGM)**:
  - `IDCAMS` — IBM Access Method Services. SMS 카탈로그에 GDG 베이스 엔트리를 생성하는 시스템 유틸리티. Java의 `java.io.File.mkdirs()`처럼 논리적 "컨테이너"를 먼저 만드는 역할이다.
  - `IEBGENER` — IBM 범용 순차 파일 복사 유틸리티. `SYSIN DD DUMMY`로 입력하면 SYSUT1을 SYSUT2로 그대로 복사(레코드 변환 없음)한다. Java의 `Files.copy(src, dst, REPLACE_EXISTING)`에 해당한다.

- **데이터셋/파일/DB 테이블**:

  | 데이터셋 이름 | 방향 | 설명 |
  |---|---|---|
  | `AWS.M2.CARDDEMO.TRANTYPE.PS` | 입력(SYSUT1) | 거래유형 참조 데이터 순차 파일, LRECL=60 FB |
  | `AWS.M2.CARDDEMO.TRANTYPE.BKUP` | GDG 베이스(정의) | 거래유형 GDG 베이스, LIMIT=5 |
  | `AWS.M2.CARDDEMO.TRANTYPE.BKUP(+1)` | 출력(SYSUT2) | 거래유형 GDG 최초 세대(첫 번째 백업) |
  | `AWS.M2.CARDDEMO.TRANCATG.PS` | 입력(SYSUT1) | 거래카테고리 참조 데이터 순차 파일, LRECL=60 FB |
  | `AWS.M2.CARDDEMO.TRANCATG.PS.BKUP` | GDG 베이스(정의) | 거래카테고리 GDG 베이스, LIMIT=5 |
  | `AWS.M2.CARDDEMO.TRANCATG.PS.BKUP(+1)` | 출력(SYSUT2) | 거래카테고리 GDG 최초 세대 |
  | `AWS.M2.CARDDEMO.DISCGRP.PS` | 입력(SYSUT1) | 공시그룹 참조 데이터 순차 파일, LRECL=50 FB |
  | `AWS.M2.CARDDEMO.DISCGRP.BKUP` | GDG 베이스(정의) | 공시그룹 GDG 베이스, LIMIT=5 |
  | `AWS.M2.CARDDEMO.DISCGRP.BKUP(+1)` | 출력(SYSUT2) | 공시그룹 GDG 최초 세대 |

  > **레코드 형식 차이**: TRANTYPE/TRANCATG는 LRECL=60, DISCGRP는 LRECL=50이다(59번, 64번, 88번 라인). 현대화 시 DTO 클래스의 필드 길이 정의 시 주의해야 한다.

- **선행/후행 잡**:
  - **선행**: 이 잡은 초기 설치 잡이므로 원칙적으로 선행 잡 없이 최초 1회 실행한다. 단, 소스 PS 파일(`TRANTYPE.PS`, `TRANCATG.PS`, `DISCGRP.PS`)이 이미 카탈로그에 존재해야 한다.
  - **후행**: 이 GDG 베이스들은 야간 배치에서 참조 데이터를 백업할 때 `(+1)` 세대로 append된다(추측). 실제 후행 잡은 별도의 배치 스케줄 정의에서 확인이 필요하다.

---

## Java/현대화 노트

### GDG의 Java 대응 개념

GDG는 Java/클라우드 생태계에 직접 대응하는 클래스가 없다. 개념적으로 가장 가까운 현대적 패턴은 다음과 같다:

```java
// GDG LIMIT(5) SCRATCH 의 Java 대응 — 롤링 백업 관리
public class RollingBackupManager {
    private static final int MAX_GENERATIONS = 5; // LIMIT(5)

    public Path createNewGeneration(Path sourceFile, Path backupBaseDir) throws IOException {
        // (0) 기존 세대 목록 정렬 (최신순)
        List<Path> generations = listGenerationsSorted(backupBaseDir);

        // (1) LIMIT 초과 시 가장 오래된 세대 삭제 → SCRATCH 동작
        if (generations.size() >= MAX_GENERATIONS) {
            Files.delete(generations.get(generations.size() - 1)); // 가장 오래된 것 삭제
        }

        // (2) 새로운 (현재+1) 세대 파일 생성 → IEBGENER DISP=(NEW,CATLG)
        Path newGen = backupBaseDir.resolve("gen_" + nextGenerationNumber(generations));
        return Files.copy(sourceFile, newGen, StandardCopyOption.REPLACE_EXISTING);
    }
}
```

클라우드 환경에서는 **S3 버킷의 객체 버전 관리(Object Versioning)** 또는 **S3 Lifecycle 정책**(`noncurrentVersionExpiration: 5`)이 GDG의 가장 직접적인 대체제다.

### AWS Mainframe Modernization(M2) 맥락

이 잡의 JOB 카드에 `'DEF DB2 GDG'`라는 어카운팅 정보가 있다(1번 라인). 이름에 DB2가 들어 있지만 실제로 DB2 SQL을 사용하지는 않는다. 해당 GDG 백업 데이터는 옵션 모듈 `app-transaction-type-db2`에서 DB2로 로드되는 참조 데이터의 원본 소스임을 나타내는 명명 관례로 보인다(추측).

### STEP50 COND 부재 주의

STEP50(세 번째 IDCAMS)에는 `COND=(0,NE)`가 없다(70번 라인). 즉 STEP40이 실패해도 STEP50은 항상 실행된다. 이는 의도적 설계일 수도 있고(세 GDG는 독립적이므로 앞 실패와 무관하게 정의 시도), 코딩 누락일 수도 있다. 현대화 시 파이프라인에서는 각 GDG 정의를 완전히 독립적인 태스크로 분리하거나, 전체를 원자적 트랜잭션으로 묶는 방향을 선택해야 한다.

### 현대화 전략 요약

| 메인프레임 구성 | AWS/Java 대응 |
|---|---|
| GDG 베이스 (`IDCAMS DEFINE`) | S3 버킷 버전 관리 활성화, 또는 DynamoDB 버전 테이블 |
| `(+1)` 세대 생성 (`IEBGENER`) | AWS SDK `s3.putObject()` 또는 `Files.copy()` |
| `LIMIT(5) SCRATCH` | S3 Lifecycle Rule `noncurrentVersionExpiration: 5` |
| `COND=(0,NE)` 조건부 실행 | Spring Batch `StepBuilderFactory.get(...).tasklet(...).build()` + `JobExecutionDecider` |
| `LRECL=60 RECFM=FB` | Java DTO 클래스 필드 총합이 60바이트(EBCDIC 기준) |
