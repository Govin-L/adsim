import type { SimulationInput } from '@/api/client'
import i18n from '@/i18n'

export interface Template {
  key: string
  getInput: () => SimulationInput
}

function t(key: string): string {
  return i18n.t(key)
}

export const templates: Template[] = [
  {
    key: 'beauty',
    getInput: () => ({
      product: {
        brandName: t('create.templates.beauty.brandName'),
        name: t('create.templates.beauty.productName'),
        price: 79,
        category: t('create.templates.beauty.category'),
        sellingPoints: t('create.templates.beauty.sellingPoints'),
        productStage: 'BESTSELLER',
        description: t('create.templates.beauty.description'),
      },
      adPlacements: [
        {
          platform: 'xiaohongshu',
          placementType: 'KOL_SEEDING',
          objectives: ['SEEDING', 'BRAND_AWARENESS'],
          format: 'VIDEO',
          budget: 10000000,
          creativeDescription: t('create.templates.beauty.creative1'),
        },
        {
          platform: 'xiaohongshu',
          placementType: 'INFO_FEED',
          objectives: ['CONVERSION', 'TRAFFIC'],
          format: 'IMAGE_TEXT',
          budget: 6000000,
          creativeDescription: t('create.templates.beauty.creative2'),
        },
        {
          platform: 'xiaohongshu',
          placementType: 'SEARCH',
          objectives: ['CONVERSION'],
          format: 'IMAGE_TEXT',
          budget: 4000000,
          creativeDescription: t('create.templates.beauty.creative3'),
        },
      ],
      totalBudget: 20000000,
      targetAudience: {
        ageRange: [18, 28],
        gender: 'female',
        region: t('create.templates.beauty.region'),
        interests: [],
      },
      competitors: [
        { brandName: t('create.templates.beauty.competitor1Brand'), price: 170, positioning: t('create.templates.beauty.competitor1Pos') },
        { brandName: t('create.templates.beauty.competitor2Brand'), price: 39, positioning: t('create.templates.beauty.competitor2Pos') },
        { brandName: t('create.templates.beauty.competitor3Brand'), price: 320, positioning: t('create.templates.beauty.competitor3Pos') },
      ],
      brandAwareness: 'WELL_KNOWN' as const,
      campaignGoal: 'ACQUISITION' as const,
    }),
  },
  {
    key: 'ecommerce',
    getInput: () => ({
      product: {
        brandName: t('create.templates.ecommerce.brandName'),
        name: t('create.templates.ecommerce.productName'),
        price: 99,
        category: t('create.templates.ecommerce.category'),
        sellingPoints: t('create.templates.ecommerce.sellingPoints'),
        productStage: 'NEW_LAUNCH',
        description: t('create.templates.ecommerce.description'),
      },
      adPlacements: [
        {
          platform: 'douyin',
          placementType: 'HASHTAG_CHALLENGE',
          objectives: ['BRAND_AWARENESS', 'SEEDING'],
          format: 'VIDEO',
          budget: 3000000,
          creativeDescription: t('create.templates.ecommerce.creative1'),
        },
        {
          platform: 'xiaohongshu',
          placementType: 'KOL_SEEDING',
          objectives: ['SEEDING', 'CONVERSION'],
          format: 'VIDEO',
          budget: 2000000,
          creativeDescription: t('create.templates.ecommerce.creative2'),
        },
        {
          platform: 'xiaohongshu',
          placementType: 'INFO_FEED',
          objectives: ['CONVERSION'],
          format: 'IMAGE_TEXT',
          budget: 1000000,
          creativeDescription: t('create.templates.ecommerce.creative3'),
        },
      ],
      totalBudget: 6000000,
      targetAudience: {
        ageRange: [18, 35],
        gender: 'female',
        region: t('create.templates.ecommerce.region'),
        interests: [],
      },
      competitors: [
        { brandName: t('create.templates.ecommerce.competitor1Brand'), price: 89, positioning: t('create.templates.ecommerce.competitor1Pos') },
        { brandName: t('create.templates.ecommerce.competitor2Brand'), price: 128, positioning: t('create.templates.ecommerce.competitor2Pos') },
      ],
      brandAwareness: 'WELL_KNOWN' as const,
      campaignGoal: 'ACQUISITION' as const,
    }),
  },
  {
    key: 'app',
    getInput: () => ({
      product: {
        brandName: t('create.templates.app.brandName'),
        name: t('create.templates.app.productName'),
        price: 0,
        category: 'App',
        sellingPoints: t('create.templates.app.sellingPoints'),
        productStage: 'NEW_LAUNCH',
        description: t('create.templates.app.description'),
      },
      adPlacements: [
        {
          platform: 'douyin',
          placementType: 'INFO_FEED',
          objectives: ['CONVERSION', 'BRAND_AWARENESS'],
          format: 'VIDEO',
          budget: 250000,
          creativeDescription: t('create.templates.app.creative1'),
        },
        {
          platform: 'xiaohongshu',
          placementType: 'KOL_SEEDING',
          objectives: ['SEEDING'],
          format: 'VIDEO',
          budget: 150000,
          creativeDescription: t('create.templates.app.creative2'),
        },
        {
          platform: 'xiaohongshu',
          placementType: 'INFO_FEED',
          objectives: ['TRAFFIC'],
          format: 'IMAGE_TEXT',
          budget: 100000,
          creativeDescription: t('create.templates.app.creative3'),
        },
      ],
      totalBudget: 500000,
      targetAudience: {
        ageRange: [18, 35],
        gender: 'all',
        region: t('create.templates.app.region'),
        interests: [],
      },
    }),
  },
]
