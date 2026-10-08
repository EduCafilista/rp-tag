#!/bin/bash
# Rebuild da toolchain NeoForge 1.21.1 (receita testada 3x) — escreve em /var/tmp
set -x
mkdir -p /var/tmp && cd /var/tmp
export JAVA_HOME=/var/tmp/java-21
export PATH=/var/tmp/java-21/bin:$PATH

# 1) JDK 21
if [ ! -x /var/tmp/java-21/bin/javac ]; then
  curl -sL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" -o jdk.tgz
  rm -rf /var/tmp/java-21 && mkdir -p /var/tmp/java-21
  tar xzf jdk.tgz -C /var/tmp/java-21 --strip-components=1
  rm -f jdk.tgz
fi
java -version 2>&1 | head -1

# 2) NFRT all-jar
if [ ! -f /var/tmp/nfrt.jar ]; then
  curl -sL "https://maven.neoforged.net/releases/net/neoforged/neoform-runtime/2.0.27/neoform-runtime-2.0.27-all.jar" -o /var/tmp/nfrt.jar
fi

# 3) neoform-edit.zip (jvmargs 1450m p/ caber no sandbox)
if [ ! -f /var/tmp/neoform-edit.zip ]; then
  curl -sL "https://maven.neoforged.net/releases/net/neoforged/neoform/1.21.1-20240808.144430/neoform-1.21.1-20240808.144430.zip" -o /var/tmp/neoform-orig.zip
  rm -rf /var/tmp/nfz && mkdir -p /var/tmp/nfz && cd /var/tmp/nfz
  # (importante) extrai o zip INTEIRO (config/ + patches/) — o NFRT le
  # arquivos de dentro (ex.: config/joined.tsrg); so o config.json quebra
  unzip -q ../neoform-orig.zip
  python3 - <<'EOF'
import json
c=json.load(open('config.json'))
c['functions']['decompile']['jvmargs']=['-Xmx1450m','-XX:+UseSerialGC']
json.dump(c,open('config.json','w'),indent=1)
EOF
  zip -qr /var/tmp/neoform-edit.zip .
  cd /var/tmp
fi

# 4) nf-binary (gameJarNoRecomp) — ~6min frio
if [ ! -f /var/tmp/nf-binary.jar ]; then
  java -Xmx100m -jar /var/tmp/nfrt.jar run \
    --neoform /var/tmp/neoform-edit.zip \
    --neoforge "net.neoforged:neoforge:21.1.249:userdev" \
    --dist joined \
    --home-dir=/var/tmp/nfrt-home \
    --work-dir=/var/tmp/nfrt-work \
    --write-result=gameJarNoRecompWithNeoForge:/var/tmp/nf-binary.jar || exit 1
fi
ls -la /var/tmp/nf-binary.jar

# 5) 48 libs do userdev
if [ ! -d /var/tmp/neoforge-libs ] || [ $(ls /var/tmp/neoforge-libs | wc -l) -lt 40 ]; then
  rm -rf /var/tmp/neoforge-libs /var/tmp/userdev.zip && mkdir -p /var/tmp/neoforge-libs
  curl -sL "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-userdev.jar" -o /var/tmp/userdev.zip
  python3 - <<'EOF'
import json,zipfile,subprocess,os
z=zipfile.ZipFile('/var/tmp/userdev.zip')
c=json.loads(z.read('config.json'))
libs=c['libraries']
repos=["https://maven.neoforged.net/releases/","https://repo1.maven.org/maven2/","https://libraries.minecraft.net/"]
# (21.1.x) 'libraries' e lista de STRINGS de coordenadas Maven
for name in libs:
    g,a,v,cl=(name.split(':')+['']*4)[:4]
    p=g.replace('.','/')+'/'+a+'/'+v+'/'+a+'-'+v+(('-'+cl) if cl else '')+'.jar'
    done=False
    for r in repos:
        out='/var/tmp/neoforge-libs/'+os.path.basename(p)
        rc=subprocess.run(['curl','-sfL',r+p,'-o',out]).returncode
        if rc==0 and os.path.getsize(out)>0: done=True; break
    if not done: print('FAIL', name)
EOF
fi

# 6+7) CLASSPATH completo (receita validada): nf-binary + TODOS os artifacts
# do cache NFRT (mojang/brigadier, etc) + libs do neoforge — sem sources/javadoc
python3 - <<'EOF'
import glob
jars = [j for j in glob.glob('/var/tmp/nfrt-home/artifacts/**/*.jar', recursive=True)
        + glob.glob('/var/tmp/neoforge-libs/*.jar')
        if 'sources' not in j and 'javadoc' not in j]
open('/var/tmp/cp.txt', 'w').write(':'.join(['/var/tmp/nf-binary.jar'] + jars))
print('CP jars:', len(jars))
EOF
wc -c /var/tmp/cp.txt
find /var/tmp -maxdepth 1 -name "*.jar" && echo TOOLCHAIN-OK
