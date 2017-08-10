# MobilityGamesAndroid
Android Apps for the Google Tango to encourage and motivate learning of mobility and orientation skills 

# Installation

You might run into some possible installation woes, which we have not ironed out.  

## Check your `.git/config` file

The `cane_game` relies on the `Android-Multiple-file-Selector-Dialog`, which is a submodule in our repo.  Make sure that in your `.git/config` you have these lines

```
[submodule "Android-Multiple-file-Selector-Dialog"]
        url = https://github.com/occamLab/Android-Multiple-file-Selector-Dialog.git
```
Then, run `git submodule update`

## Have ndk-libraries

Make sure you have ndk (native development kit) installed, since we rely on some C and C++ code.
[https://developer.android.com/ndk/guides/index.html](https://developer.android.com/ndk/guides/index.html)

## Download OpenCV Android SDK

You can get an Occam Lab download, hosted on the Google Drive
[Link](https://drive.google.com/file/d/0B0UHkPLHsgyoZnFQOEQyNzZ3YTQ/view?usp=sharing)

Unzip and Extract this archive.  We extract it to our Downloads folder

## Link the Open CV Android SDK with `Android.mk`

Inside `cane_game/src/main/jni/Android.mk`, make sure the following line points to your OpenCV Android SDK installation

```
CVROOT := /home/{YOUR_COMPUTER_NAME}/Downloads/OpenCV-android-sdk/sdk/native/jni
```


