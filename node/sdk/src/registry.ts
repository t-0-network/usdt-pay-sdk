import { createRegistry } from "@bufbuild/protobuf";
import { file_tzero_v1_pay_issuer_issuer } from "./gen/tzero/v1/pay/issuer/issuer_pb.js";
import { file_tzero_v1_pay_acquirer_acquirer } from "./gen/tzero/v1/pay/acquirer/acquirer_pb.js";
import { file_tzero_v1_pay_lp_lp } from "./gen/tzero/v1/pay/lp/lp_pb.js";
import { file_tzero_v1_pay_common } from "./gen/tzero/v1/pay/common_pb.js";
import { file_tzero_v1_pay_validate } from "./gen/tzero/v1/pay/validate_pb.js";

/**
 * Registry covering the pay contract protos. Every file is listed explicitly:
 * `createRegistry` does NOT walk a file's imports, so relying on the service
 * files to pull in common.proto and validate.proto leaves the custom
 * predefined-rule extensions (`valid_tx_hash`, `valid_address`) unresolvable
 * at validation time. A new proto file added by a sync must be added here.
 */
export const payRegistry = createRegistry(
  file_tzero_v1_pay_issuer_issuer,
  file_tzero_v1_pay_acquirer_acquirer,
  file_tzero_v1_pay_lp_lp,
  file_tzero_v1_pay_common,
  file_tzero_v1_pay_validate,
);
