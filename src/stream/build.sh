src_path=$(dirname $(readlink -e $0))
build_dir=$src_path/build
mkdir $build_dir
cd $build_dir
javac  -source 1.8 -target 1.8 -classpath $(storm classpath):../lib/* -d $build_dir $src_path/*.java
jar cf $src_path/task2.jar *
cd -
rm -r $build_dir
