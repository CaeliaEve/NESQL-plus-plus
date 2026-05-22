# NeoNEI / NESQL++ 鏈€缁堝紑鍙戞竻鍗?

> 鐩爣锛氭妸 NeoNEI 浠庘€滆繍琛屾椂鎷兼暟鎹殑缃戠珯鈥濇帹杩涙垚鈥滅綉椤靛舰寮忕殑 NEI 鏈湴绱㈠紩鍣ㄢ€濓細NESQL++ 鍦ㄦ父鎴忓唴瀵煎嚭瀹屾暣濂戠害锛孨eoNEI 缂栬瘧鎴愬唴瀹瑰鍧€鍙戝竷鍖咃紝鍓嶇鍩轰簬 Manifest / Atlas / Bundle 绉掑紑锛屽苟鏀寔鏈潵鍏叡缃戠珯 CDN 鍒嗗彂銆?

## 鎬讳綋鍘熷垯

- 涓嶄负鈥滄妧鏈珮绾ф劅鈥濊縼绉诲ぇ妗嗘灦锛涗紭鍏堣В鍐崇湡瀹炵摱棰堬細瀵煎嚭鎱€佺紦瀛橀敊涔便€佽创鍥?鍔ㄥ浘缂哄け銆侀厤鏂逛涪澶便€佸揩閫熺炕椤靛崱椤裤€?
- 淇濈暀 Express銆丼QLite銆乂ue銆丆anvas/WebGL 涓荤嚎锛涜ˉ寮哄绾︺€佹姤鍛娿€佸唴瀹瑰鍧€銆佸閲忔瀯寤恒€佸墠绔祫婧愯皟搴︺€?
- NESQL++ 鑳借В鍐崇殑鏁版嵁/瀵煎嚭闂浼樺厛鍦?NESQL++ 瑙ｅ喅锛汵eoNEI 鍙秷璐圭ǔ瀹氬绾﹀苟鍋氬彂甯冨寘浼樺寲銆?
- 姣忎釜闃舵蹇呴』鏈夊彲楠岃瘉浜х墿锛屼笉鑳介潬浜哄伐缈婚〉闈㈢寽娴嬫垚鍔熴€?

## 鏆備笉浣滀负涓荤嚎

- [ ] 涓嶅叏闈㈣縼绉?NestJS / Fastify銆?
- [ ] 涓嶇敤 GraphQL 鏇夸唬褰撳墠 REST + 闈欐€?publish bundle銆?
- [ ] 涓嶅紩鍏?Redis 浣滀负褰撳墠涓荤紦瀛樺眰銆?
- [ ] 涓嶆妸涓婚〉鍙充晶娴忚鍖哄洖閫€鎴?DOM 铏氭嫙鍒楄〃銆?
- [ ] 涓嶆妸 WebGPU 鏀惧叆杩戞湡涓荤嚎銆?

## P0锛氬畬鏁存€ф姤鍛婁笌鐗堟湰濂戠害

### NESQL++
- [x] 杈撳嚭 `canonical/export-manifest.json`锛氳褰曚粨搴撳悕銆乸rofile銆乻election銆丯ESQL++鐗堟湰銆佺敓鎴愭椂闂淬€佸叧閿骇鐗╄矾寰勩€?
- [x] 杈撳嚭 `canonical/export-health-report.json`锛氬吋瀹?鍗囩骇鐜版湁 validation report锛屽寘鍚己鍥俱€佸姩鍥俱€丄tlas銆佸鐐瑰姩鍥惧彲鐤戦」銆佸疄浣撴ā鍨嬨€佸鏂瑰潡绛夌粺璁°€?
- [x] 杈撳嚭 `canonical/export-stage-timings.json`锛氳褰曟瘡涓鍑洪樁娈佃€楁椂銆?
- [x] 杈撳嚭 `canonical/stage-checksums.json`锛氳褰曞叧閿骇鐗╁ぇ灏忎笌 SHA-256锛岀敤浜庝簩娆″鍑?缂栬瘧璺宠繃銆?
- [x] 杈撳嚭缂哄け璧勬簮鏍锋湰锛氳创鍥剧己澶便€佸姩鐢荤己澶便€乼imeline frame 缂哄け銆佺┖ manifest銆?

### NeoNEI
- [x] 杈撳嚭 `publish/build-report.json` 鍜屽彲璇?`publish/build-report.html`銆?
- [ ] 妫€鏌?source manifest銆乥rowser layout銆乤tlas銆乤nimated atlas銆乺ecipe bundle銆乻earch pack 鏄惁鍖归厤銆?
- [x] 鎶ュ憡鍩虹鍙戝竷鍖呰鐩栫巼銆佷骇鐗╂暟閲忋€佸帇缂╃巼涓?warnings锛涚己鍥剧巼/闂彿鐜?绌洪厤鏂圭巼鍚庣画缁х画缁嗗寲銆?

## P1锛氬唴瀹瑰鍧€鍙戝竷绯荤粺 CAS

- [ ] 鎵€鏈夋牳蹇?publish 浜х墿鎸夊唴瀹?hash 鍛藉悕銆?
- [x] `publish/manifest.json` 璁板綍 datasetVersion銆乻ourceSignature銆乮temsHash銆乺ecipesHash銆乥rowserLayoutHash銆乤tlasHash銆乤nimatedAtlasHash銆?
- [x] 鍓嶇 runtime cache key 瀹屽叏缁戝畾 manifest identity銆?
- [ ] CDN 闈欐€佽祫婧愪娇鐢?immutable hash URL銆?
- [x] 鏃х紦瀛樻薄鏌撴椂鑳藉鑷姩澶辨晥銆?

## P2锛氭祻瑙堝尯 NEI 鍘熺敓浣撴劅

- [x] NESQL++ 瀵煎嚭 NEI 娴忚鎺掑簭涓庢姌鍙犲垎缁勩€?
- [x] NeoNEI 鍓嶇娑堣垂 browser layout 骞舵樉绀哄垎缁勩€?
- [x] browser layout 涓?atlas index 寮虹粦瀹氥€?
- [x] 鍒嗙粍灞曞紑缁撴灉棰勮绠楋紝閬垮厤杩愯鏃跺ぇ璁＄畻銆?
- [x] 姣忛〉璧勬簮渚濊禆棰勮绠楋細褰撳墠椤点€佸墠涓€椤点€佸悗涓€椤甸渶瑕佸摢浜?atlas/animated atlas銆?
- [x] 蹇€熺炕椤靛彇娑堟棫椤佃祫婧愯姹傦紝鍙繚鐣欏綋鍓嶇洰鏍囬〉鏈€楂樹紭鍏堢骇銆?
- [x] atlas miss 涓嶉樆濉炰富绾跨▼锛岀己鍥惧紓姝ヨˉ榻愩€?

## P3锛氶厤鏂归〉绉掑紑 Bundle

- [x] 鐢熸垚 `publish/item-recipe-bundles/shard-*.json`銆?
- [x] 鐢熸垚 `publish/recipe-ui-bundles/shard-*.json`銆?
- [x] 姣忎釜 item bundle 鍖呭惈 producedBy銆乽sedIn銆乻ummaryGroups銆乵achineGroups銆乫irstPageRecipes銆乽iPayloadRefs銆乤ssetRefs銆?
- [x] 鍓嶇鎵撳紑閰嶆柟浼樺厛璇诲彇 static item bundle + IndexedDB runtime cache锛屽悗绔?API 鍙綔 fallback锛汷PFS 鐣欑粰 P6 澶ц祫婧愬眰銆?
- [x] 甯歌鐗╁搧閰嶆柟鎽樿鐩爣 <100ms锛屽畬鏁撮厤鏂圭粍鐩爣 <300ms锛圢eoNEI 宸插缓绔?recipe-open-budget / recipe-bootstrap-resolved Gate锛涘叏閲忔暟鎹疄娴嬬户缁敱 Gate C 杩借釜锛夈€?

## P4锛氬鍑?/ 缂栬瘧澧為噺鍖?

- [x] NESQL++ 闃舵鎷嗗垎锛歞ata銆乮mages銆乤nimated-images銆乺ender-contracts銆乥rowser-layout銆乵ultiblocks銆乪ec-models銆乺ecipe-layout-contracts銆乤tlas-pack锛坰tage timing/checksum 鐜板湪杈撳嚭 stable family/skippable contract锛夈€?
- [ ] 姣忛樁娈垫湁 checksum锛屽彲璺宠繃鏈彉鍖栬緭鍑恒€?
- [ ] 鍥剧墖娓叉煋鎸?item/render/texture signature 璺宠繃銆?
- [ ] Atlas packer 鎸?sprite hash / atlas page hash 璺宠繃銆?
- [x] NeoNEI 缂栬瘧鎸?item shard銆乺ecipe shard銆乻earch shard銆乤tlas page銆乺ecipe bundle shard 澧為噺璺宠繃锛圢eoNEI 闈欐€?bundle 宸叉敼涓?write-if-changed锛宐uild report 杈撳嚭 written/skipped 璁℃暟锛沘tlas page 澧為噺缁х画娌跨敤鍐呭 hash/manifest 鏍￠獙锛夈€?

## P5锛氭悳绱綋绯诲崌绾?

- [x] NeoNEI 缂栬瘧闃舵寮曞叆 SQLite FTS5 浣滀负鎼滅储鍖呮瀯寤?鍚庣鍏滃簳绱㈠紩銆?
- [x] 瀛楁瑕嗙洊涓枃鍚嶃€佽嫳鏂囧悕銆佹嫾闊炽€侀瀛楁瘝銆佹ā缁勫悕銆乮tem id銆乼ooltip 鍏抽敭璇嶃€?
- [x] 鍓嶇缁х画浣跨敤 browserSearchWorker + hot/full shard锛屼笉鍥為€€瀹炴椂 SQL 鎼滅储銆?

## P6锛氬墠绔ぇ璧勬簮缂撳瓨鍗囩骇

- [x] IndexedDB 瀛?manifest銆乻earch pack銆乥rowser layout銆乮tem bundle銆佸皬 JSON銆?
- [x] OPFS 璇曠偣瀛樺ぇ atlas銆乤nimated atlas銆佸ぇ recipe bundle銆乪ntity preview銆乵ultiblock preview銆?
- [x] 缂撳瓨灞傜骇锛歁emory LRU 鈫?IndexedDB metadata 鈫?OPFS blob 鈫?HTTP/CDN 鈫?backend fallback銆?

## P7锛歐orker / OffscreenCanvas 娓叉煋澧炲己

- [ ] Worker 璁＄畻娴忚鍖?layout銆乨raw commands銆佽祫婧愪緷璧栥€?
- [ ] 涓荤嚎绋嬪彧璐熻矗浜や簰鍜屾渶缁堢粯鍒躲€?
- [ ] OffscreenCanvas 浣滀负澧炲己璺緞锛屽繀椤讳繚鐣?fallback銆?

## P8锛氬叕鍏辩綉绔欏伐绋嬪寲

- [ ] publish 闈欐€佽祫婧?CDN 鍖栵紝hash 鏂囦欢闀挎湡 immutable 缂撳瓨銆?
- [ ] manifest 鐭紦瀛?/ no-cache銆?
- [ ] 鍚庣鍙礋璐?fallback API銆乭ealth report銆乤dmin rebuild銆佹暟鎹増鏈垏鎹€佽瘖鏂帴鍙ｃ€?
- [ ] 绠＄悊鎺ュ彛澧炲姞 token銆乺ate limit銆佽姹傛牎楠屻€佺粨鏋勫寲鏃ュ織銆丱penAPI銆?

## 褰撳墠鎺ㄨ繘椤哄簭

1. P0 NESQL++ 鎶ュ憡涓?checksum 灏忛棴鐜€?
2. P0 NeoNEI build report 灏忛棴鐜€?
3. P1 manifest identity / cache key 寮虹粦瀹氥€?
4. P2 browser layout 涓?atlas 璧勬簮渚濊禆缁戝畾銆?
5. P3 item-centric recipe bundle銆?
6. P4 澧為噺瀵煎嚭涓庡閲忕紪璇戙€?




