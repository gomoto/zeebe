// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package main

import (
	"context"
	"errors"
	"fmt"
	"io/ioutil"
	"os"
	"os/exec"
	"regexp"
	"runtime"
	"strings"
	"testing"
	"time"

	"github.com/google/go-cmp/cmp"
	"github.com/stretchr/testify/suite"
	"github.com/zeebe-io/zeebe/clients/go/internal/containersuite"
	"github.com/zeebe-io/zeebe/clients/go/pkg/zbc"
)

var zbctl string

const (
	// NOTE: taken from https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string
	semVer = `(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*` +
		`|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?`
)

type integrationTestSuite struct {
	*containersuite.ContainerSuite
}

var tests = []struct {
	name       string
	setupCmds  []string
	envVars    []string
	cmd        string
	goldenFile string
}{
	{
		name:       "print help",
		cmd:        "help",
		envVars:    []string{"HOME=/tmp"},
		goldenFile: "testdata/help.golden",
	},
	{
		name:       "print version",
		cmd:        "version",
		envVars:    []string{"HOME=/tmp"},
		goldenFile: "testdata/version.golden",
	},
	{
		name:       "missing insecure flag",
		cmd:        "status",
		envVars:    []string{"HOME=/tmp"},
		goldenFile: "testdata/without_insecure.golden",
	},
	{
		name: "using insecure env var",
		cmd:  "status",
		// we need to set the path so it evaluates $HOME before we overwrite it
		envVars:    []string{fmt.Sprintf("%s=true", zbc.InsecureEnvVar), fmt.Sprintf("PATH=%s", os.Getenv("PATH"))},
		goldenFile: "testdata/topology.golden",
	},
	{
		name: "using json flag",
		cmd:  "status --output=json",
		// we need to set the path so it evaluates $HOME before we overwrite it
		envVars:    []string{fmt.Sprintf("%s=true", zbc.InsecureEnvVar), fmt.Sprintf("PATH=%s", os.Getenv("PATH"))},
		goldenFile: "testdata/topology_json.golden",
	},
	{
		name:       "deploy workflow",
		cmd:        "--insecure deploy testdata/model.bpmn testdata/job_model.bpmn --resourceNames=model.bpmn,job.bpmn",
		goldenFile: "testdata/deploy.golden",
	},
	{
		name:       "create instance",
		setupCmds:  []string{"--insecure deploy testdata/model.bpmn"},
		cmd:        "--insecure create instance process",
		goldenFile: "testdata/create_instance.golden",
	},
	{
		name:       "create worker",
		setupCmds:  []string{"--insecure deploy testdata/job_model.bpmn", "--insecure create instance jobProcess"},
		cmd:        "create --insecure worker jobType --handler echo",
		goldenFile: "testdata/create_worker.golden",
	},
	{
		name:       "empty activate job",
		cmd:        "--insecure activate jobs jobType --maxJobsToActivate 0",
		goldenFile: "testdata/empty_activate_job.golden",
	},
	{
		name:       "single activate job",
		setupCmds:  []string{"--insecure deploy testdata/job_model.bpmn", "--insecure create instance jobProcess"},
		cmd:        "--insecure activate jobs jobType --maxJobsToActivate 1",
		goldenFile: "testdata/single_activate_job.golden",
	},
	{
		name: "double activate job",
		// we deploy on the end again to spent more time in setup phase to avoid a race condition, that we can activate more jobs then one
		setupCmds:  []string{"--insecure deploy testdata/job_model.bpmn", "--insecure create instance jobProcess", "--insecure create instance jobProcess", "--insecure deploy testdata/job_model.bpmn"},
		cmd:        "--insecure activate jobs jobType --maxJobsToActivate 2",
		goldenFile: "testdata/double_activate_job.golden",
	},
}

func TestZbctlWithInsecureGateway(t *testing.T) {
	output, err := buildZbctl()
	if err != nil {
		fmt.Println(string(output))
		t.Fatal(fmt.Errorf("couldn't build zbctl: %w", err))
	}

	suite.Run(t,
		&integrationTestSuite{
			ContainerSuite: &containersuite.ContainerSuite{
				WaitTime:       time.Second,
				ContainerImage: "camunda/zeebe:current-test",
			},
		})
}

func (s *integrationTestSuite) TestCommonCommands() {
	for _, test := range tests {
		s.T().Run(test.name, func(t *testing.T) {
			for _, cmd := range test.setupCmds {
				if _, err := s.runCommand(cmd); err != nil {
					t.Fatal(fmt.Errorf("failed while executing set up command '%s': %w", cmd, err))
				}
			}

			cmdOut, err := s.runCommand(test.cmd, test.envVars...)
			if errors.Is(err, context.DeadlineExceeded) {
				t.Fatal(fmt.Errorf("timed out while executing command '%s': %w", test.cmd, err))
			}

			goldenOut, err := ioutil.ReadFile(test.goldenFile)
			if err != nil {
				t.Fatal(err)
			}
			want := strings.Split(string(goldenOut), "\n")
			got := strings.Split(string(cmdOut), "\n")

			if diff := cmp.Diff(want, got, cmp.Comparer(composeComparer(cmpIgnoreNums, cmpIgnoreVersion))); diff != "" {
				t.Fatalf("%s: diff (-want +got):\n%s", test.name, diff)
			}
		})
	}
}

func composeComparer(cmpFuncs ...func(x, y string) bool) func(x, y string) bool {
	return func(x, y string) bool {
		for _, cmpFunc := range cmpFuncs {
			if cmpFunc(x, y) {
				return true
			}
		}

		return false
	}
}

func cmpIgnoreVersion(x, y string) bool {
	versionRegex := regexp.MustCompile(semVer)
	newX := versionRegex.ReplaceAllString(x, "")
	newY := versionRegex.ReplaceAllString(y, "")

	return newX == newY
}

func cmpIgnoreNums(x, y string) bool {
	numbersRegex := regexp.MustCompile(`\d`)
	newX := numbersRegex.ReplaceAllString(x, "")
	newY := numbersRegex.ReplaceAllString(y, "")

	return newX == newY
}

// runCommand runs the zbctl command and returns the combined output from stdout and stderr
func (s *integrationTestSuite) runCommand(command string, envVars ...string) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	args := append(strings.Fields(command), "--address", s.GatewayAddress)
	cmd := exec.CommandContext(ctx, fmt.Sprintf("./dist/%s", zbctl), args...)

	cmd.Env = append(cmd.Env, envVars...)
	return cmd.CombinedOutput()
}

func buildZbctl() ([]byte, error) {
	switch runtime.GOOS {
	case "linux":
		zbctl = "zbctl"
	case "darwin":
		zbctl = "zbctl.darwin"
	default:
		return nil, fmt.Errorf("can't run zbctl tests on unsupported OS '%s'", runtime.GOOS)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()

	// we need to build all binaries, because this is run after the go build stage on CI and will overwrite the binaries
	cmd := exec.CommandContext(ctx, "./build.sh")
	cmd.Env = append(os.Environ(), "RELEASE_VERSION=release-test", "RELEASE_HASH=1234567890")
	return cmd.CombinedOutput()
}
