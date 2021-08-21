cd "${BASH_SOURCE%/*}/"

PATH=${PATH}:/usr/local/bin

# Start KIND
/usr/local/bin/kind create cluster --config kind.yaml

# Start registry
if [ "$(docker inspect -f "{{.State.Running}}" kind-registry 2>/dev/null)" != "true" ]; then
  docker run -d --rm -p 127.0.0.1:5000:5000 --name kind-registry registry:2
  echo "Started registry"
fi

docker network connect kind kind-registry || true

# Document the local registry per in-flight KEP 1755
kubectl apply -f - <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:${reg_port}"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF

# Install Knative Serving
kubectl apply -f https://github.com/knative/serving/releases/download/v0.25.0/serving-crds.yaml
kubectl apply -f https://github.com/knative/serving/releases/download/v0.25.0/serving-core.yaml
# Install Kourier for http routing
kubectl apply -f https://github.com/knative-sandbox/net-kourier/releases/download/v0.25.0/kourier.yaml
# Convert the Kourier service to NodePort
kubectl patch svc -n kourier-system kourier --type merge --patch '{"spec": {"type": "NodePort", "ports": [{"name": "http2", "nodePort": 31080, "port": 80, "targetPort": 8080}]}}'
# Set Kourier as the default ingress class
kubectl patch configmap/config-network --namespace knative-serving --type merge --patch '{"data":{"ingress.class":"kourier.ingress.networking.knative.dev"}}'
 
# Update Knative Serving configuration with a domain name (using the public IPv4 address)
kubectl patch configmap -n knative-serving config-domain -p '{"data": {"127.0.0.1.nip.io": ""}}'

# Update Knative Serving to not resolve tags in local registry, because it expects SSL and there isn't.
kubectl patch -n knative-serving configmap config-deployment --patch '{"data":{"registriesSkippingTagResolving": "localhost:5000,kind-registry:5000,kind.local,ko.local,dev.local"}}'

# Install Knative Eventing
kubectl apply -f https://github.com/knative/eventing/releases/download/v0.25.0/eventing-crds.yaml
kubectl apply -f https://github.com/knative/eventing/releases/download/v0.25.0/eventing-core.yaml
# Install in-memory development components
kubectl apply -f https://github.com/knative/eventing/releases/download/v0.25.0/in-memory-channel.yaml
kubectl apply -f https://github.com/knative/eventing/releases/download/v0.25.0/mt-channel-broker.yaml

# Set up Tekton
kubectl apply --filename https://storage.googleapis.com/tekton-releases/pipeline/latest/release.yaml
kubectl apply -f https://raw.githubusercontent.com/tektoncd/catalog/master/task/buildpacks/0.3/buildpacks.yaml
# Create our local buildpack
kubectl apply -f buildpack-pipeline.yaml

sleep 10  # Make sure the previous components have time to come up for defaulting
# Create the default broker
kubectl apply -f - <<EOF
apiVersion: eventing.knative.dev/v1
kind: Broker
metadata:
  name: default
  annotations:
    eventing.knative.dev/broker.class: MTChannelBasedBroker
spec:
  config:
    apiVersion: v1
    kind: ConfigMap
    name: config-br-default-channel
    namespace: knative-eventing
EOF

# Start Octant in the background:
octant --disable-open-browser --listener-addr 0.0.0.0:8081 >&/dev/null &
