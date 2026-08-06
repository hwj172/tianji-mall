#!/bin/bash
# 重建 product 表 FULLTEXT 索引 (name, description) 使用 ngram 解析器（支持中文搜索）
# 修复: ① 索引缺失 → MATCH 报错 500；② 中文搜索空结果（默认 parser 对连续中文不分词）
# 用法: bash fix-product-fulltext.sh
set -e
SSH_ARGS="-i C:/Users/黄文杰/.ssh/id_rsa -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=C:/Users/黄文杰/.ssh/known_hosts"
VM="root@192.168.150.11"

echo "==> 重建 FULLTEXT 索引（ngram 解析器）"
ssh $SSH_ARGS $VM 'docker exec mysql mysql -uroot -proot tianji_mall -e "ALTER TABLE product DROP INDEX ft_product_name_desc; ALTER TABLE product ADD FULLTEXT INDEX ft_product_name_desc (name, description) WITH PARSER ngram; SELECT COUNT(*) AS fulltext_index_count FROM information_schema.statistics WHERE table_schema=\"tianji_mall\" AND table_name=\"product\" AND index_type=\"FULLTEXT\";" 2>/dev/null'

echo "==> 完成（fulltext_index_count >= 1 即成功；测试中文搜索可再跑 curl 验证）"
