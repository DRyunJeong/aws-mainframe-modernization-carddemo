# CLAUDE.md

이 파일은 이 저장소에서 작업할 때 Claude Code(claude.ai/code)에게 제공하는 가이드입니다.

## 프로젝트 개요

CardDemo는 주로 COBOL로 작성된 메인프레임 신용카드 관리 애플리케이션으로, AWS가 메인프레임 마이그레이션/현대화 도구(discovery, analysis, transformation, 성능 테스트)를 점검하고 시연하기 위해 설계했습니다. 코드는 실제 레거시 메인프레임 코드베이스처럼 분석 도구에 부담을 주도록 코딩 스타일, 데이터 형식, 데이터셋 유형을 의도적으로 혼합하고 있습니다. 로컬 런타임은 존재하지 않습니다 — 이 애플리케이션은 워크스테이션이 아니라 실제 메인프레임(또는 메인프레임 에뮬레이션 환경)의 CICS/VSAM/JCL 위에서 동작하도록 설계되었습니다.

## 저장소 구조

- `app/` — 베이스 애플리케이션 소스, 아티팩트 유형별로 구성:
  - `cbl/` — COBOL 프로그램 (CICS 온라인 프로그램과 배치 프로그램이 함께 섞여 있음)
  - `cpy/` — 여러 프로그램이 공유하는 copybook(데이터 구조)
  - `bms/` + `cpy-bms/` — BMS 화면 맵 소스와 그로부터 생성된 맵 copybook
  - `jcl/` — 배치 작업 JCL (파일 로드, 배치 처리, 유틸리티)
  - `proc/` — JCL이 사용하는 카탈로그 프로시저
  - `csd/` — 트랜잭션/프로그램/맵셋 설치용 CICS 리소스 정의(CSD)
  - `asm/` + `maclib/` — 어셈블러 모듈(타이머 제어용 MVSWAIT, 날짜 변환용 COBDATFT)과 해당 매크로
  - `data/EBCDIC/` 및 `data/ASCII/` — 초기 로드를 위한 샘플 데이터셋(각각 메인프레임 인코딩과 워크스테이션 인코딩)
  - `catlg/` — VSAM 카탈로그 목록 참조 자료(`LISTCAT.txt`)
  - `scheduler/` — CA7 및 Control-M 작업 스케줄러 정의
  - `app-authorization-ims-db2-mq/`, `app-transaction-type-db2/`, `app-vsam-mq/` — **선택적 확장 모듈**들로, 각각 자체 `cbl/`, `cpy/`, `bms/`, `jcl/`, `csd/`를 갖춘 독립적인 구조이며 필요에 따라 `dcl/`/`ddl/`/`ctl/`(DB2) 또는 `ims/`(IMS DB)를 포함합니다. 각 모듈에는 설치 방법과 DB2/IMS/MQ 리소스 설정을 설명하는 자체 README가 있습니다.
- `samples/jcl/` 및 `samples/proc/` — 프로그램이 메인프레임에서 어떻게 컴파일/링크 편집되는지 보여주는 예제 컴파일 JCL/proc(`BATCMP`, `BMSCMP`, `CICCMP`, `CICDBCMP`, `IMSMQCMP`, `BUILDONL.prc`, `BUILDBAT.prc`, `BUILDBMS.prc`, `BLDCIDB2.prc`) — IGYCRCTL 컴파일 단계, DFHEILID/BMS 변환 단계, HEWL 링크 편집 단계 등을 포함. 이들은 템플릿으로 참고할 것이며, 그대로 사용하지 말고 HLQ와 라이브러리 이름을 대상 환경에 맞게 조정해야 합니다.
- `samples/m2/` — 실제 메인프레임이 아닌 AWS Mainframe Modernization(M2) 및 UniKix 플랫폼에서 CardDemo를 실행하기 위한 패키지된 런타임.
- `scripts/` — FTP 터널을 통해 실제 메인프레임 작업을 수행하기 위한 로컬 헬퍼 스크립트(아래 참고).
- `diagrams/` — README에서 참조되는 아키텍처/흐름도(`Application-Flow-User.png`, `Application-Flow-Admin.png`, `auth_flow.png` 등).

## 코드 작업 방법

이 저장소에는 로컬 빌드, 테스트, 린트 도구가 없습니다(Makefile, CI 설정, 단위 테스트 프레임워크 없음) — "빌드"한다는 것은 실제 메인프레임, CICS 리전, 또는 M2/UniKix 런타임에서 COBOL/BMS/어셈블러 소스를 컴파일하는 것을 의미합니다.

### 로컬 문법 검사
GnuCOBOL(`cobc`)는 실제 빌드가 아니라 대략적인 문법 점검 용도로만 로컬에서 사용할 수 있습니다:
```
cobc -I app/cpy/ -fsyntax-only --std=ibm-strict app/cbl/<PROGRAM>.cbl
```
이 방법으로는 EXEC CICS/EXEC SQL/DL-I 호출이 메인프레임 환경 밖에서는 해석될 수 없기 때문에 CICS/VSAM/DB2/IMS 관련 문제는 잡아낼 수 없습니다.

### scripts/ — 원격 메인프레임 작업 흐름
이 스크립트들은 `localhost:2121`에서 메인프레임(Ensono)으로의 FTP 터널이 이미 실행 중이라고 가정합니다. 각 스크립트는 터널 존재 여부를 확인하며, 없으면 "FTP Tunnel to Ensono not running."이라는 메시지를 출력하고 중단됩니다. 따라서 해당 터널과 메인프레임 접근 권한이 없으면 사용할 수 없습니다:
- `upld_module.sh <path> <module_type>` — 소스 파일을 80바이트 메인프레임 레코드 길이로 패딩하고(`pad.awk`) 해당 PDS 멤버(예: `AWS.M2.CARDDEMO.CBL(member)`)로 FTP 전송합니다.
- `remote_compile.sh <file> <ext> <basename>` — `.cbl` 파일만 허용하며, `compile_batch.jcl.template`에 멤버 이름을 치환한 후 결과 JCL을 FTP(`filetype=JES`)로 제출합니다.
- `remote_submit.sh <file.jcl>` — 임의의 JCL 파일을 FTP로 제출합니다.
- `remote_refresh.sh` — 전체 데이터 갱신 JCL 시퀀스를 제출합니다(CLOSEFIL → ACCTFILE/CARDFILE/XREFFILE/CUSTFILE/TRANFILE/DISCGRP/TCATBALF/TRANCATG/TRANTYPE/DUSRSECJ → OPENFIL).
- `run_full_batch.sh` — 전체 배치 사이클을 제출합니다(데이터 갱신 후 POSTTRAN → INTCALC → TRANBKP → COMBTRAN → TRANIDX → OPENFIL), 루트 README의 "Running Batch Jobs" 시퀀스를 그대로 따릅니다.
- `run_posting.sh`, `run_interest_calc.sh` — 배치 사이클의 개별 단계를 제출합니다.
- `git-addSrcVersionInfo.sh <file>` — `git describe`/커밋 수에서 파생된 버전 정보 주석을 소스 파일에 추가합니다(확장자별 주석 문법: `cbl/cob/cpy` → `      *`, `jcl/prc/proc` → `//*`, `bms` → `*`, `py` → `##`). 버전 스탬프를 원한다면 모듈 업로드 전에 실행하십시오. 단, 부수 효과로 로컬 git 저장소의 태그를 변경합니다(설정된 `app_version`을 제외한 모든 태그를 삭제), 따라서 자동화된 환경에서 실행할 때는 주의가 필요합니다.

이 스크립트들은 모두 실시간 FTP 터널과 실제 메인프레임 데이터셋을 필요로 하므로, 이 샌드박스에서는 실행할 수 없습니다 — 이 스크립트들을 변경할 때는 "실행하여 검증"하기보다는 코드 리뷰/정적 분석으로 접근해야 합니다.

## 아키텍처 노트

### 프로그램 유형 및 네이밍
- **CICS 온라인 프로그램**: `CO*` 접두사를 사용합니다(예: `COSGN00C` 로그인, `COMEN01C` 메인 메뉴, `COACTVWC`/`COACTUPC` 계정 조회/수정, `COCRDLIC`/`COCRDSLC`/`COCRDUPC` 카드 목록/조회/수정, `COTRN00C`/`COTRN01C`/`COTRN02C` 거래 목록/조회/추가, `COBIL00C` 청구서 결제, `COADM01C` 관리자 메뉴, `COUSR0*C` 사용자 관리). 각 프로그램은 하나의 CICS 트랜잭션 ID와 하나의 BMS 맵셋에 1:1로 매핑됩니다(전체 트랜잭션/프로그램/맵셋 매트릭스는 루트 README의 Application Inventory 표 참고).
- **배치 프로그램**: `CB*` 접두사를 사용합니다(예: `CBACT01C`–`CBACT04C` 계정 파일 처리, `CBCUS01C` 고객, `CBTRN01C`–`CBTRN03C` 거래 처리/리포팅, `CBSTM03A`/`CBSTM03B` 명세서 생성, `CBEXPORT`/`CBIMPORT` 데이터 내보내기/가져오기). 배치 프로그램은 CICS를 통하지 않고 `FILE-CONTROL`에 선언된 플랫 VSAM/순차 파일을 직접 읽고 씁니다.
- CICS 프로그램의 일반적인 흐름: 공통 copybook(commarea용 `COCOM01Y`, `cpy-bms/`의 화면 copybook, 제목/날짜/메시지 copybook, `DFHAID`/`DFHBMSCA`)을 COPY로 포함시키고, `LINKAGE SECTION`을 통해 `DFHCOMMAREA`를 받아, `EIBCALEN`/유사 대화형(pseudo-conversational) 상태를 기반으로 `MAIN-PARA`에서 분기합니다.
- `app/cpy/`의 copybook들은 VSAM 파일의 표준 레코드 레이아웃입니다(예: `CVACT01Y` 계정, `CVACT02Y` 카드, `CVCUS01Y` 고객, `CVACT03Y` 카드/계정/고객 상호 참조, `CVTRA0*Y` 거래/카테고리/공시 그룹 레코드, `CSUSR01Y` 사용자 보안). 이 레이아웃들은 온라인(CICS) 프로그램과 배치 프로그램 사이에서 공유되며, 루트 README의 Installation 섹션에 있는 데이터셋 표와 직접 대응됩니다.

### 선택적 모듈은 베이스 코드 수정이 아닌 추가 방식
세 가지 `app/app-*` 확장 모듈(DB2 거래 유형 관리, IMS/DB2/MQ 승인, VSAM/MQ 계정 추출)은 베이스 `cbl/`/`cpy/` 파일을 수정하지 않고(관리자 메뉴 옵션 활성화를 제외하면) 새로운 트랜잭션/프로그램/copybook을 베이스 애플리케이션 위에 추가하는 방식으로 동작합니다. 이 선택적 기능들 중 하나를 확장하거나 수정해야 할 때는, 새로운 패턴을 도입하기보다 변경 범위를 해당 모듈의 하위 디렉터리로 한정하고 해당 모듈의 README에 문서화된 기존 DCL/DDL/IMS 컨벤션을 따르십시오.

### 데이터 흐름
온라인(CICS) 프로그램과 배치 프로그램은 모두 동일한 VSAM KSDS 파일(계정, 카드, 고객, 상호 참조, 거래)을 대상으로 동작합니다 — 베이스 애플리케이션에는 별도의 데이터베이스가 없습니다. DB2와 IMS DB는 선택적 모듈에서만 도입됩니다(DB2의 거래 유형 참조 데이터, IMS HIDAM의 승인 레코드와 DB2의 사기 추적). 루트 README의 "Running Batch Jobs" 표(그리고 `scripts/run_full_batch.sh`에 반영됨)는 야간 처리가 어떻게 흘러가는지에 대한 표준 시퀀스입니다: 마스터 파일 갱신 → 거래 입력(post) → 이자 계산 → 백업 → 합치기(combine) → 대체 인덱스 재구성 → CICS용 파일 재오픈.
