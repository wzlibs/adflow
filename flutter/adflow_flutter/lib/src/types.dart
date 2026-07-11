import 'generated/adflow_api.g.dart';

/// Alias sạch (không prefix `P`) quanh các kiểu Pigeon-sinh đã là value object phẳng, không cần 1
/// lớp Dart mirror riêng - chỉ [AdState] (`ad_state.dart`) cần wrap thật vì có field khác nhau
/// theo từng nhánh (sealed hierarchy đúng nghĩa, không phải 1 class phẳng với field null-able).
typedef AdFlowError = PAdFlowError;
typedef RewardItem = PRewardItem;
typedef AdRevenueEvent = PAdRevenueEvent;
typedef BlockReason = PBlockReason;
typedef BannerSize = PBannerSize;
typedef ConsentStatus = PConsentStatus;
typedef PrivacyOptionsRequirement = PPrivacyOptionsRequirement;
typedef DebugGeography = PDebugGeography;
