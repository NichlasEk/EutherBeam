# Third-party notices

EutherBeam's Samsung H-series interoperability work was informed by these independently published implementations:

- `tdudek/samsung-remote-models-2014-and-newer`, declared MIT in its package metadata: <https://github.com/tdudek/samsung-remote-models-2014-and-newer>
- `sermayoral/ha-samsungtv-encrypted`, Apache License 2.0: <https://github.com/sermayoral/ha-samsungtv-encrypted>

EutherBeam contains a Kotlin port of the Samsung H-series white-box transform and its lookup table,
derived from the MIT-licensed `tdudek/samsung-remote-models-2014-and-newer` implementation. The rest
of the cryptographic operations use Android/JCA primitives. No third-party runtime is bundled.
