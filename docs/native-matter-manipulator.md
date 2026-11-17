# Matter Manipulator upgrade state

Implementation batch: 2026-10-22. Not yet compiled or run in the unified verification batch.

The reader targets the installed `matter-manipulator-0.0.51-GTNH.jar`. The current upstream checkout has a different persistence law and must not substitute for this version. `MatterManipulatorSource` binds the registered `RecipeInstallUpgrade.INSTANCE`, registered manipulator/MetaItem classes, tier permissions, metadata/default mappings and native item-list specimens. A disabled recipe is an explicit registration exclusion, before unrelated specimen or codec readiness is required.

The recipe scans the entire crafting inventory and accepts two occupied cells. Stack count is not a matching threshold. Any registered MetaItem instance participates in the literal metadata lookup, including explicit null entries and the native default result. The upgrade must be allowed for the manipulator tier and absent from its decoded installed-upgrade BitSet. The result copies the offered manipulator item, metadata and count; workbench consumption/container/tool rules remain separate conditions.

In 0.0.51, state save replaces the entire root. Unknown root fields are discarded. Absent JSON version is 0; migration 0→1 removes the seven legacy configuration fields. BitSet uses Gson's numeric-array format, not the later base64 string format. Capability accumulation occurs during load from the old bits; installing a new bit does not repeat that accumulation before this save.

`MatterManipulatorCodec` gives an owned Gson its own defaults, contexts and caches. It compares the native default factory definitions and admits only the four pinned custom adapters. It does not use the borrowed native Gson for serialization. Singleton weighted block states require callback admission before their native air check; unknown block/metadata/intrinsic/architecture callbacks still produce an explicit fault. This boundary must be included in native verification rather than hidden behind a default texture or guessed NBT.

Native list, registered-class and declared-bitset specimens have distinct origins. Generated bit combinations are witnesses, not proof of obtainability. Input and normalized NBT share a reader-wide 32 MiB budget and 65,536 specimen count. Component and state samples are reused within the reader; full source and normalization checks run when sealing. Close releases owned caches and borrowed bindings.

The new `matterManipulatorUpgrade` process, two input predicates and `matterManipulator` edit are connected to Compiler and NeoNEI. Exact output NBT is available only for recorded native normalizations. An unobserved state remains conditional on successful native decoding and encoding. Frontend matching, details and materials do not turn a unit specimen into guaranteed arbitrary-batch consumption or output. Chinese and English explanations are separate.

Queued lightweight checks: installed native load/save and upgrade-bit parity, cache ownership and source drift; Rust BitSet parsing; four Node matching/normalization/strict-gate cases. The derived native test JAR and new contracts have not yet been prepared or generated. Existing semantic.67 validation does not cover this process.
