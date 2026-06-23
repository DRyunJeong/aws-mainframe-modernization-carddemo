# CardDemo 배치 COBOL → Java 마이그레이션 (batch_processing_workflow)

CardDemo의 **배치 COBOL 프로그램**들을 idiomatic Java 17로 이주한 결과물이다. 규제 금융
도메인이므로 **동작 보존 + byte-for-byte 일치**가 최우선이고, 정답(oracle)은 *원본 COBOL을
GnuCOBOL로 실행한 결과*다(코드 해석이 아님 — `MIGRATION_STRATEGY.md`).

## 대상 범위

GnuCOBOL로 실행 가능한 **순수 배치 프로그램**만 oracle 기반 byte-for-byte 검증이 가능하다.

| 프로그램 | 기능 | 상태 |
|---|---|---|
| `CBACT04C` | 이자 계산(control-break, BigDecimal 산술) | ✅ 완료 |
| `CBACT02C` | 카드 파일 읽기·출력(SYSOUT) | ✅ 완료 |
| `CBACT03C` | 카드 교차참조 읽기·출력 | ✅ 완료 |
| `CBCUS01C` | 고객 파일 읽기·출력 | ✅ 완료 |
| `CBTRN01C` | 일일거래 검증(조회) | ✅ 완료 |
| `CBTRN02C` | 거래 포스팅(다중 KSDS 갱신) | ✅ 완료 |
| `CBTRN03C` | 거래 상세 리포트(편집필드·페이지/총계) | ✅ 완료 |
| `CBACT01C` | 계정 읽기 → 다중 출력(COMP-3·가변길이·어셈블러 스텁) | ✅ 완료 |
| `CBEXPORT` | 5 마스터 → 멀티레코드 export(COMP·COMP-3·zoned 혼합) | ✅ 완료 |
| `CBIMPORT` | export → 5 분할 출력 + 에러(타입 라우팅) | ✅ 완료 |
| `CBSTM03A`/`B` | 계정 명세서(text+HTML, 서브프로그램 I/O·ALTER/GO TO) | ✅ 완료 |

**제외(약 26개)**: CICS 온라인(CO*, `EXEC CICS`), DB2(`EXEC SQL`), IMS(`EXEC DLI`), MQ는
GnuCOBOL로 실행 불가 → byte-for-byte oracle 검증이 원천적으로 불가능하여 제외. 유틸 `COBSWAIT`
(어셈블러 `MVSWAIT` 의존)·`CSUTLDTC`(`CEEDAYS` LE 서비스 의존)도 동일 사유로 제외. 이들은 CICS/
DB2/IMS 에뮬레이션 환경을 갖춘 별도 후속 과제로 분리한다.

**배치 전체(11개) 마이그레이션 완료** — 위 표의 모든 순수 배치 프로그램이 byte-for-byte oracle 검증됨.
각 프로그램의 실측 동작은 아래 "프로그램별 발견 동작"에 정리. 남은 비배치(CICS/DB2/IMS/MQ 등)는 위
제외 사유대로 후속 과제.

## 빠른 실행

```bash
# 1) 데이터셋 생성(샘플 정규화 + 합성) — 프로그램별
python3 src/test/cobol/<prog>/data.py
# 2) 골든 마스터 생성: 원본 <PROG>.cbl을 GnuCOBOL로 실행해 정답 캡처 (cobc 필요)
src/test/cobol/run_oracle.sh <prog>
# 3) 이중 하니스 테스트: Java를 같은 입력에 돌려 골든과 byte-for-byte 비교
./gradlew test
```
전제: GnuCOBOL `cobc` 3.2(INDEXED 핸들러 BDB), `python3`, JDK(설치된 21에서 `--release 17`).

## 구조 (다중 프로그램 프레임워크)

```
src/main/java/com/carddemo/batch/
  BatchOutputs.java          # 프로그램 출력(논리 파일명→바이트) — 다중 출력 지원
  AbendException.java
  io/   ZonedDecimal         # COBOL zoned-decimal(native ASCII 부호) 코덱 (전 프로그램 공용)
        Comp3                # COMP-3 packed-decimal 코덱(부호 nibble 0xC/0xD signed, 0xF unsigned)
        Comp                 # COMP binary 코덱(big-endian·2의 보수·IBM 폭 2/4/8)
        Cobol                # 고정폭 필드/데이터셋 IO(LINE/RECORD SEQUENTIAL 리더)
        RecordPrinter        # "KSDS 순차읽기→SYSOUT DISPLAY" 공용 헬퍼
        AccountStore/XrefStore/DiscgrpStore  # VSAM KSDS analog
  domain/                    # copybook → 공유 record (재사용)
        AccountRecord(CVACT01Y) CardRecord(CVACT02Y) CardXrefRecord(CVACT03Y)
        CustomerRecord(CVCUS01Y) TranCatBalRecord(CVTRA01Y)
        DisclosureGroupRecord(CVTRA02Y) TransactionRecord(CVTRA05Y)
  cbact04c/ cbact03c/ cbact02c/ cbcus01c/ …   # 프로그램별 패키지(<Prog>Main + 로직)

src/test/cobol/
  run_oracle.sh              # 일반화: run_oracle.sh <prog> [case…]
  oracle_lib.py              # 공용 데이터 헬퍼(부호 transcode·native 인코딩)
  <prog>/{HARNESS.cbl, oracle.conf, data.py}   # 프로그램별 oracle 자산
src/test/resources/
  datasets/<prog>/{sample, synthetic/<case>}/  # 정규화 입력
  golden/<prog>/<case>/{stdout.txt, *.dat}      # oracle 캡처(정답)
src/test/java/com/carddemo/batch/
  AbstractGoldenMasterTest   # 재사용 베이스(데이터셋 자동탐색, 출력별 byte 비교·마스킹)
  OracleSupport              # per-program 경로/마스킹 헬퍼
  <Prog>GoldenMasterTest     # 프로그램별 thin 서브클래스
```

## 검증(이중 하니스)
- `run_oracle.sh <prog>`가 **무수정** 원본을 `cobc`로 컴파일·실행. VSAM 의존 → `<prog>/HARNESS.cbl`이
  *파일 I/O만 각색*(정규화 입력을 BDB INDEXED/순차로 적재 → `CALL` → 출력/수정 KSDS 덤프). SYSOUT만
  출력하는 프로그램은 stdout 캡처가 골든.
- `AbstractGoldenMasterTest`가 Java를 같은 입력에 돌려 출력별로 골든과 **byte-for-byte** 비교.
  비결정 타임스탬프 필드만 양쪽 동일 마스킹.

## 문서화한 편차·결정 (전 프로그램 공통, CBACT04C에서 확립)
1. **부호 인코딩 = GnuCOBOL native ASCII**: 양수(0 포함) 끝자리 평문 `'0'..'9'`, 음수 끝자리 `'p'..'y'`
   (0x70+digit). 샘플의 EBCDIC overpunch(`{`,`A`..`I`,`}`,`J`..`R`)는 GnuCOBOL이 **산술에서 오독**하므로
   정규화 시 부호 바이트만 native로 transcode(**값 보존**). `ZonedDecimal`이 동일 규칙으로 read/write.
2. **`COMPUTE` 절사 = `RoundingMode.DOWN`**(`/1200` 등), 필드 용량 초과 = 상위자리 절사. `BigDecimal`만 사용.
3. **byte-for-byte 범위 = GnuCOBOL ASCII 실행 출력**. 메인프레임 EBCDIC 바이트 동치는 범위 외.
4. **비결정 타임스탬프**(`FUNCTION CURRENT-DATE`)는 비교 전 양쪽 동일 마스킹.
5. **계정 raw-image 보존**: REWRITE 시 변경 필드만 재인코딩, 나머지는 입력 바이트 그대로.

### 프로그램별 발견 동작 (oracle로 실측 확정)
- **CBACT04C**: ① 메인 루프 `ELSE`(마지막 계정 반영)가 도달 불가 → **마지막 계정 미반영**(레거시 latent 동작 보존,
  `docs/cbl/CBACT04C.md`는 정반대로 서술). ② `MOVE '05'→TRAN-CAT-CD`(9(4))=`"0005"`, `TRAN-DESC` STRING 잔여·
  `FILLER`=`0x00`. ③ 샘플 `ACCT-GROUP-ID` 공백 → DEFAULT 그룹 폴백.
- **CBACT03C / CBCUS01C**: 각 레코드를 **2회 DISPLAY**(GET-NEXT 단락 + 메인 루프 양쪽 활성). 키 순서로 정렬.
- **CBACT02C**: GET-NEXT의 DISPLAY가 **주석 처리** → 레코드 **1회** DISPLAY(03C/CUS01C와 대비).
- **CBTRN01C**: ① 메인 루프 XREF/ACCOUNT 조회(L170-184)가 EOF 가드 **밖**에 있어, EOF 직후 마지막
  레코드의 **조회가 한 번 더 실행**(레코드 DISPLAY는 가드되어 생략). ② 계정 미존재 시 메시지 2건
  (`3000-READ-ACCOUNT` + 메인 루프). ③ DALYTRAN은 순차파일이라 **파일 순서** 처리(정렬 안 함).
- **CBACT01C**: ① **COMP-3 packed**(`OUT-ACCT-CURR-CYC-DEBIT`, 배열 cyc-debit) → `Comp3` 코덱(7바이트, 부호 nibble 0xC/0xD).
  ② **OUT-ACCT-CURR-CYC-DEBIT는 입력 debit=0일 때만 2525.00 설정**, ≠0이면 미설정 → FD 영역에 직전 값 잔존(영속 값으로 재현).
  ③ **가변길이 레코드**(VBRCFILE, RECORDING MODE V): GnuCOBOL 4바이트 prefix(`2바이트 BE 길이 + 00 00`). ④ 어셈블러 `COBDATFT`는
  GnuCOBOL 실행 불가 → COBOL 스텁(`COBDATFT.cbl`, YYYY-MM-DD→YYYYMMDD)으로 대체(oracle·Java 동일 로직, 문서화된 편차).
- **CBTRN03C**: ① 리포트 금액은 COBOL 편집 PIC(`-ZZZ,ZZZ,ZZZ.ZZ` 상세 / `+...` 총계, 선행 0 억제·콤마·floating sign) →
  `Cobol.editFloatingSign`. ② **EOF 중복합산**: 마지막 레코드의 `TRAN-AMT`가 EOF에서 page/account 총계에 한 번 더 더해짐.
  ③ **마지막 계정 총계 미출력**(계정 break에서만 출력). ④ `NEXT SENTENCE`(범위 밖 거래)는 루프를 종료. ⑤ 페이지 20줄.
  ⑥ stdout의 signed `DISPLAY`는 11자리 크기 + 후행 부호(`Cobol.displaySigned9v2`).
- **CBTRN02C**: ① TCATBAL 생성(`2700-A`)이 `INITIALIZE`를 쓰는데 **INITIALIZE는 FILLER를 초기화하지 않음**
  → 생성 레코드의 22바이트 FILLER는 `TRAN-CAT-BAL-RECORD` 버퍼에 남은 **직전 read 레코드의 FILLER**를 상속
  (`TcatbalStore`가 영속 버퍼로 재현). ② 거부 1건 이상이면 RETURN-CODE 4. ② 검증(카드→계정→한도→만료) 후
  포스팅(TRANSACT 작성·TCATBAL 누적·ACCOUNT 갱신). ③ 일일거래는 DISPLAY 안 함(TCATBAL 생성 메시지만).
- **CBEXPORT**: ① 5 마스터(customer/account/xref/transaction/card)를 **키 순서**(INDEXED SEQUENTIAL)로 읽어
  500바이트 멀티레코드 export 파일을 타입 순서(C→A→X→T→D)로, 전역 증가 시퀀스로 작성. ② 레코드 = 헤더(REC-TYPE·
  TIMESTAMP·SEQUENCE-NUM 9(9) **COMP**·BRANCH·REGION) + 460바이트 타입별 페이로드(REDEFINES). ③ 숫자 필드는 copybook대로
  재인코딩: DISPLAY→**COMP**(`EXP-…-CYC-DEBIT` S9(10)V99·`EXP-…-ID` 9(9)/9(11)·CVV 9(3)), DISPLAY→**COMP-3**
  (`CURR-BAL`·`CASH-CREDIT-LIMIT` S9·`FICO` 9(3) **unsigned→0xF**), DISPLAY→DISPLAY(zoned, 값보존). ④ `INITIALIZE
  EXPORT-RECORD`가 페이로드(elementary X(460))를 **공백**으로 → 미기록 갭·FILLER=공백. ⑤ 26바이트 TIMESTAMP는 실행시각 →
  골든 마스킹. **파일 I/O 각색**: `EXPFILE`이 `RECORD KEY IS EXPORT-SEQUENCE-NUM`인데 그 키가 WORKING-STORAGE에 있어
  GnuCOBOL이 거부 → 순차 WRITE 전용이므로 `ORGANIZATION SEQUENTIAL`로 변경(레코드 바이트·순서 불변, 비즈니스 로직 무수정).
- **CBIMPORT**: ① export 파일(시퀀스 순)을 읽어 `EVALUATE EXPORT-REC-TYPE`로 5개 정규화 파일로 분할, 미지 타입은 에러 파일로.
  COMP/COMP-3→DISPLAY 역디코드(zoned). ② `WRITE target-RECORD`(FROM 없음)는 FD 레코드 영역을 그대로 기록 — `INITIALIZE`는
  FILLER 미초기화, GnuCOBOL OUTPUT FD 레코드 영역 초기값이 **low-values** → 각 출력의 후행 FILLER=`0x00`(프로브로 실측).
  ③ 에러 레코드는 WS(130바이트, `|`구분)에서 132바이트 FD로 `WRITE FROM` → 영숫자 MOVE라 끝 2바이트 **공백** 패딩;
  선두 26바이트 `ERR-TIMESTAMP`(`FUNCTION CURRENT-DATE`)는 골든 마스킹. ④ EXPFILE도 동일 사유로 `ORGANIZATION SEQUENTIAL`로
  각색(원시 500바이트 record-sequential 입력). 검증은 CBEXPORT 출력을 입력으로 잇는 **체인**(+미지타입 합성).
- **CBSTM03A/B**: ① **서브프로그램 I/O** — `CBSTM03A`가 범용 파일 핸들러 `CBSTM03B`(DD명+오퍼레이션으로 4개 KSDS:
  TRNXFILE(키=card16+id16, 350B)·XREFFILE·CUSTFILE(랜덤)·ACCTFILE(랜덤))를 `CALL`. ② **제어블록 각색**: 선두
  PSA/TCB/TIOT 주소지정이 미초기화 `PSAPTR` 역참조로 off-mainframe 크래시 → **DISPLAY 진단 전용**이라 스텁(명세서 출력 불변).
  ③ **copybook 탭**: `CUSTREC.cpy` 탭 들여쓰기 → 정규화본을 copybook 경로 앞에 둠(내용 동일). ④ **출력 = record-sequential**
  (텍스트 80B·HTML 100B, 개행 없음). ⑤ **편집 PIC**: 잔액 `9(9).99-`(0채움)·거래/총계 `Z(9).99-`(0억제), 모두
  **후행 부호**(양수 공백/음수 `-`); 10자리 잔액→9(9) **상위 절사**. ⑥ **STRING 의미**: 이름은 `DELIMITED BY ' '`(각 첫 공백까지),
  HTML 이름/주소는 `DELIMITED BY '  '`(첫 **이중공백**까지) + 리터럴 2칸 — 빈 미들네임이면 이름에 이중공백이 생겨 HTML이 거기서 잘림.
  ⑦ 제어 흐름은 `ALTER`/`GO TO` 재진입 디스패처지만 순효과는 "TRNX 전부 테이블화 → XREF 순회하며 카드별 명세서". ⑧ 거래 없는 카드도
  총계 `$ .00`로 명세서 출력.

### oracle 하니스 운영 주의 (다중 KSDS 프로그램)
- **DD명 정확 일치**: CBTRN02C는 거래 KSDS를 `ASSIGN TO TRANFILE`(≠`TRANSACT`)로 쓴다 — 환경변수 DD명을 원본과 맞출 것.
- **`COB_FILE_PATH` 미설정**: 설정 시 GnuCOBOL이 절대경로 LINE SEQUENTIAL의 `AT END`를 못 잡아 무한루프. 절대경로만 쓰고 설정하지 않는다.
- **다중 파일 1개 하니스 회피**: 파일이 많은 단일 하니스에서 GnuCOBOL codegen 이슈로 멈춤 → CBTRN02C는 작은 **로더/더퍼**(파일별 2개) + 프로그램 **직접 실행**(`RUN_DIRECT=1`)으로 분리.
- **COMP/COMP-3 바이트 표현은 프로브로 고정**: 작은 COBOL 프로브를 `--std=ibm`으로 실행해 실측 — COMP는 **big-endian·2의 보수·폭 2/4/8**(예 9(9)=0x12345678→`12 34 56 78`), FD OUTPUT 레코드 초기값은 **low-values(0x00)**. 코덱(`Comp`/`Comp3`)·FILLER 처리를 이 실측값에 핀.
- **컴파일 불가 구문은 파일 I/O만 각색**: `RECORD KEY`가 WORKING-STORAGE 필드라 GnuCOBOL이 거부하는 등 원본이 그대로 컴파일 안 되면, `oracle.conf`가 원본을 읽어 **해당 SELECT의 ORGANIZATION만 치환**한 `*.gen.cbl`을 생성해 컴파일(레코드 바이트·읽기/쓰기 순서·로직 불변). `.gen.cbl`은 빌드 산출물(gitignore).
- **프로그램 간 체인**: CBIMPORT 입력 = CBEXPORT 출력. cbexport `capture_outputs`가 (마스킹된) export 파일을 `datasets/cbimport/sample/expdata.dat`로 복사 → cbimport `data.py`가 거기서 미지타입 케이스 파생. 생성 순서: `data.py cbexport → run_oracle cbexport → data.py cbimport → run_oracle cbimport`(알파벳순이라 `run_all_oracles.sh`도 cbexport 먼저).
- **비포터블 진단 코드 스텁**: 데이터 산출물에 영향 없는 메인프레임 전용 구문(예 CBSTM03A의 PSA/TCB/TIOT `DISPLAY`)은 `oracle.conf`의 `*.gen.cbl` 생성 시 `CONTINUE`로 스텁(출력 파일 불변).
- **서브프로그램 링크/copybook override**: `EXTRA_CBL`로 호출 서브프로그램(CBSTM03B)을 함께 링크. 탭 들여쓰기 등으로 GnuCOBOL이 못 읽는 copybook은 정규화본을 두고 `INCDIRS="-I <dir>"`로 copybook 경로 **앞**에 끼움(run_oracle가 `${INCDIRS}` 다음 `-I app/cpy` 순서로 컴파일).

## 재생성
데이터·코드 변경 시: `python3 src/test/cobol/<prog>/data.py` → `src/test/cobol/run_oracle.sh <prog>`
(골든 갱신) → `./gradlew test`. 골든의 출처는 항상 이 cobc 실행이다. 판별력 sanity(잘못된 RoundingMode면
산술 프로그램이 실패)는 `InterestTest`로 고정.
