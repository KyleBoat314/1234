# Boat House Budget — Android App

This project includes a GitHub Actions cloud builder, so you can create the APK without installing Android Studio on your phone.

## Build the APK from your phone

1. Create a free GitHub account if you do not already have one.
2. Create a new repository. A **private** repository is fine if you do not want the project public.
3. Upload the **contents of this project folder** to the repository. Make sure `.github/workflows/build-apk.yml` is included.
4. Open the repository on GitHub and tap **Actions**.
5. Select **Build Android APK**.
6. Tap **Run workflow**, then tap the green **Run workflow** button.
7. When the build finishes, open that workflow run.
8. Under **Artifacts**, download **Boat-House-Budget-APK**.
9. Unzip the downloaded artifact. Inside is **Boat-House-Budget.apk**.
10. Tap the APK on your Android phone to install it. Android may ask you to allow installs from your browser or Files app.

The workflow also rebuilds automatically whenever you push changes to the `main` or `master` branch.

## App details

- Package: `com.boathouse.budget`
- Minimum Android: Android 7.0 / API 24
- Target/compile SDK: API 35
- Build type produced by the cloud workflow: debug APK
- Data stays local on the device

## Updating the app

Keep the same package name and increase `versionCode` in `app/build.gradle` before installing a future version over the current one.
