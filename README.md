# MathMotorVideo — restoration source project

This Android Studio project is a clean reconstruction base for restarting MathMotorVideo from the files that could be recovered. It keeps the package name `com.mathmotor.video`, generates a 300-frame deterministic motion world, builds a separate scenario layer, runs `PHYSX / FRAME TEST`, renders a 10-second vertical MP4 locally, saves it under `Movies/MathMotorVideo`, and keeps a local history.

## Build

The GitHub Actions workflow builds the debug APK and runs JVM unit tests. Open the repository's **Actions** tab to view the build and download the `MathMotorVideo-restored-debug` artifact if the workflow succeeds.

## Important limits

This is **not** an exact source recovery of V7. The V7 APK was recovered, but the V7 ZIP contains only an APK and the old development repository has no usable source tree. See `RESTORATION_STATUS.md` for the recovered-file inventory and missing modules. The project has not been compiled on a local Android SDK or tested on a physical device yet.

The output is a simple local-rendered visual animation to verify and restart the generation/render/test path; it does not yet reproduce the original V7 social-data learning pipeline, advanced scenario selector, or audio layer.
