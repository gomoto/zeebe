#!/bin/bash
pr_dir=post-renderers
cat <&0 > $pr_dir/all.yaml
kubectl kustomize $pr_dir && rm $pr_dir/all.yaml
