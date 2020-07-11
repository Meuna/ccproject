src_path=$(dirname $0)

stop-yarn.sh
sleep 5
start-yarn.sh

bash $src_path/batch/load_data.sh
bash $src_path/batch/batch_all.sh
bash $src_path/batch/q3.sh
