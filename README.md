# Neodymium 2

> [!NOTE]
> **AI Utilization Disclosure (AI 사용 명시)**  
> 본 프로젝트의 코드 설계, 최적화 모듈 구현, 테스트 및 문서화 과정에는 AI 코딩 어시스턴트(**Google Antigravity**)가 활용되었습니다.  
> *This project was developed, optimized, and documented with the assistance of AI (**Google Antigravity**).*

**Neodymium 2**는 **Minecraft 26.3 (Fabric)** 및 **ServerCore** 환경을 위한 구역 인지형(Region-Aware) 서버 틱 최적화 모드입니다.  
지정된 **바닐라 민감도 보호 구역(농장·회로·주민 시설 등)** 내부는 바닐라 틱과 동작을 100% 보존하고, 보호 구역 외부의 야생 영역에만 공격적인 엔티티·월드·스폰·주민 Brain 최적화를 적용합니다.

---

## 주요 기능

* **구역 인지형 ServerCore 연동**: 보호 구역 내부 엔티티는 항상 정상 틱을 유지하고, 구역 외부 엔티티는 동적 활성 범위(EAR) 및 틱 간격을 적용합니다.
* **주민 AI (Brain) 유지보수 최적화**: 구역 외부의 거래/대기 주민 Brain 연산을 생략하면서도 거래 재입고, 가격 변동, 좀비 치료 할인, 마을의 영웅 선물, 작업대 탐색(`PoiRevisionGrid`)은 바닐라와 동일하게 유지합니다.
* **구역 외 스폰 · 엔티티 · 월드 최적화**: 구역 외 자연 스폰 간격 조절, 원거리 AI/밀치기 생략, 유휴 블록 엔티티·유체·랜덤 틱 감속 및 레이지 청크 투사체 정리를 수행합니다.
* **단독 사용 & 외부 구역 플러그인 연동 지원**:
  * **단독 사용 (Standalone)**: 별도의 구역 모드 없이 내장 `/neodymium2 chunk` 명령어로 보호 청크를 직접 지정할 수 있습니다.
  * **외부 연동 (Public API)**: `NeodymiumApi` 및 `NeodymiumRegionProvider`를 통해 `YAWPManager` 등 외부 구역 관리 모드와 연동할 수 있습니다.

---

## 요구 사항

* **Minecraft**: `26.3`
* **Fabric Loader**: `>=0.19.3`
* **Java**: `>=25`
* **필수 모드**: `fabric-api`, `servercore` (`1.5.20+26.3`)
* **선택 연동 모드**: `yawp`, `yawpmanager`, `carpet-fga-addition`, `farmersdelight`, `brewinandchewin`, `carpet-tis-addition`

---

## 명령어 (`/neodymium2`)

관리자 권한(OP)이 필요하며, 채팅창 클릭형 UI를 제공합니다.

### 기본 관리
* `/neodymium2 status` — 현재 MSPT/TPS, 보호 구역 수, 모듈별 최적화 통계 확인
* `/neodymium2 modules` — 활성화된 최적화 모듈 목록 확인
* `/neodymium2 regions [page]` — 보호 구역 및 보호 청크 목록 확인 (`[이동]`, `[관리]`, `[해제]` 버튼 제공)
* `/neodymium2 nametags [page]` — 로드된 이름표 엔티티 보호/차단 현황 확인
* `/neodymium2 reload` — `config/neodymium2/config.json` 및 `regions.json` 다시 불러오기

### 단독 사용용 청크 보호 지정 (`/neodymium2 chunk`)
* `/neodymium2 chunk toggle` — 현재 서 있는 청크의 바닐라 민감도 보호 ON / OFF 토글
* `/neodymium2 chunk add` — 현재 서 있는 청크 보호 추가
* `/neodymium2 chunk add <radius>` — 현재 청크 중심 정사각형 반경(`0~16` 청크) 범위 보호 추가
* `/neodymium2 chunk add <chunkX> <chunkZ>` — 지정한 청크 좌표 보호 추가
* `/neodymium2 chunk add <x1> <z1> <x2> <z2>` — 직사각형 청크 범위 보호 추가
* `/neodymium2 chunk remove [<chunkX> <chunkZ>]` — 현재(또는 지정) 청크 보호 해제

---

## Public API (`kr.harusarri.neodymium2.api`)

외부 구역 관리 모드(예: `YAWPManager`)에서 `Neodymium 2`를 호출할 때 사용하는 공개 API입니다.

```java
// 1. 외부 구역 제공자 등록
NeodymiumApi.registerRegionProvider(myRegionProvider);

// 2. 특정 구역의 바닐라 민감도 조회 및 변경
boolean sensitive = NeodymiumApi.isVanillaSensitive("minecraft:overworld", "my_region");
NeodymiumApi.setVanillaSensitive("minecraft:overworld", "my_region", !sensitive);

// 3. 외부 구역 변경/이름 변경/삭제 이벤트 알림
NeodymiumApi.onRegionMutated();
NeodymiumApi.onRegionRenamed("minecraft:overworld", "old_id", "new_id");
NeodymiumApi.onRegionRemoved("minecraft:overworld", "deleted_id");

// 4. 위치/엔티티 보호 여부 조회
boolean entityProtected = NeodymiumApi.isProtected(entity);
boolean blockProtected  = NeodymiumApi.isProtectedBlock(level, blockPos);
boolean chunkProtected  = NeodymiumApi.isProtectedChunk(level, chunkX, chunkZ);
```

---

## 라이선스

* [MIT License](./LICENSE)
