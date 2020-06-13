#include "source_file.h"

#include <jni.h>

#include <sys/types.h>
#include <dirent.h>
#include <ctype.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <android/log.h>
#include <string.h>

extern "C"
{


bool endswith(const char *str, const char *suffix) {
	size_t lenstr = strlen(str);
	size_t lensuffix = strlen(suffix);
	if (lensuffix > lenstr) return 0;
	return strncmp(str + lenstr - lensuffix, suffix, lensuffix) == 0;
}
unsigned char *as_unsigned_char_array(JNIEnv *env, jbyteArray array) {
	int len = env->GetArrayLength(array);
	auto *buf = new unsigned char[len];
	env->GetByteArrayRegion(array, 0, len, reinterpret_cast<jbyte *>(buf));
	return buf;
}
float tim = 0;

extern "C" JNIEXPORT void JNICALL
Java_onion_chat_AudioCallActivity_audioCallSound(JNIEnv *env, jclass clazz, jbyteArray input) {
	unsigned char *buffer = as_unsigned_char_array(env, input);
	unsigned len = env->GetArrayLength(input);
	for (unsigned i = 0; i < len; i++, tim += 0.000125f)
		buffer[i] = (unsigned char) ((sin(6.2831f * 440.0f * tim) * exp(-10.0 * abs(sin(tim * 3.0f)))) * -127);
	env->SetByteArrayRegion(input, 0, len, reinterpret_cast<jbyte *>(buffer));
}

extern "C" JNIEXPORT void JNICALL
Java_onion_chat_AudioCallActivity_audioVoice(JNIEnv *env, jclass clazz, jbyteArray input) {
	unsigned char *buffer = as_unsigned_char_array(env, input);
	unsigned len = env->GetArrayLength(input);
	unsigned sum = 0;
	for (unsigned i = 0; i < len; i++)
		sum += abs((int) buffer[i] - 127);
	sum /= len;
	if (abs(((int) sum) - 127) > 20 && abs(((int) sum) - 127) < 200) {
		for (unsigned i = 0; i < len; i++)
			buffer[i] = (unsigned char) fmin(((200.0 / abs((int) sum - 127)) * (unsigned) buffer[i]), UCHAR_MAX);
	}
	env->SetByteArrayRegion(input, 0, len, reinterpret_cast<jbyte *>(buffer));
}

JNIEXPORT void JNICALL Java_onion_chat_Native_killTor(JNIEnv *env, jclass clazz) {
	__android_log_print(ANDROID_LOG_INFO, "PROCESS", "TRYING TO KILL PROCESS\n");
	DIR *d = opendir("/proc");
	dirent *de;

	while ((de = readdir(d)) != 0) {
		int pid = atol(de->d_name);

		if (pid <= 0) continue;

		char namepath[1024];
		sprintf(namepath, "/proc/%i/cmdline", pid);

		char name[1024] = {0};
		if (int namefd = open(namepath, O_RDONLY)) {
			read(namefd, name, sizeof(name) - 1);
			close(namefd);
		}

		if (endswith(name, "/tor") || endswith(name, "/ftor") || endswith(name, "/ctor")) {

			__android_log_print(ANDROID_LOG_INFO, "PROCESS", "FOUND %i %s\n", pid, name);

			if (kill(pid, SIGKILL) == 0) {
				__android_log_print(ANDROID_LOG_INFO, "PROCESS", "KILLED %i %s\n", pid, name);
			} else {
				__android_log_print(ANDROID_LOG_INFO, "PROCESS", "FAILED TO KILL %i %s\n", pid,
				                    name);
			}
		}

	}

	closedir(d);

}

}