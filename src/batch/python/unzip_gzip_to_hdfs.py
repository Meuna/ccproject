import sys
import os
import posixpath
from subprocess import Popen, PIPE


def main(local_path, dfs_path):
    for path, _, filenames in os.walk(local_path):
        for filename in filenames:
            basename, _ = os.path.splitext(filename)
            filepath = os.path.join(path, filename)
            dfs_filepath = posixpath.join(dfs_path, basename + '.gz')
            unzip_pipes = Popen(['unzip', '-p', filepath, '-x', '*.html'], stdout=PIPE)
            gzip_pipes = Popen(['gzip'], stdin=unzip_pipes.stdout, stdout=PIPE)
            hdfs_pipes = Popen(['hdfs', 'dfs', '-put', '-', dfs_filepath], stdin=gzip_pipes.stdout)
            hdfs_pipes.wait()


if __name__ == '__main__':
    local_path = sys.argv[1]
    dfs_path = sys.argv[2]
    main(local_path, dfs_path)
